import { createReadStream, createWriteStream } from 'node:fs';
import fs from 'node:fs/promises';
import path from 'node:path';
import { pipeline } from 'node:stream/promises';
import { randomUUID } from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import jwt from 'jsonwebtoken';
import { z } from 'zod';
import type { AppContext, AuthHandler } from '../types.js';
import { AppError, badRequest, conflict, forbidden, notFound } from '../errors.js';
import { activeSpaceId } from './couples.js';
import { personalSpaceId } from './spaces.js';
import { resolveLocalObjectPath } from '../storage/local.js';
import { assertObjectKeyBelongsToSpace, buildObjectKey } from '../storage/object-key.js';
import {
  createStorageProvider,
  type DownloadVariant,
  type StoredObjectInfo,
  type StorageProviderName,
} from '../storage/provider.js';

const allowedMime = new Set([
  'image/jpeg', 'image/png', 'image/webp', 'image/heic',
  'video/mp4', 'video/quicktime',
]);
const avatarMime = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/heic']);
const tokenSchema = z.object({
  fileName: z.string().trim().min(1).max(200),
  mimeType: z.string().refine((value) => allowedMime.has(value), '不支持的媒体类型'),
  sizeBytes: z.number().int().positive().max(300 * 1024 * 1024),
  purpose: z.preprocess(
    (value) => (value == null ? 'media' : value),
    z.enum(['media', 'avatar']),
  ),
}).strict();
const assetIdSchema = z.object({ assetId: z.string().uuid() }).strict();
const signSchema = z.object({ variant: z.enum(['original', 'thumb']).default('original') }).strict();

interface StorageClaims extends jwt.JwtPayload {
  type: 'upload' | 'download';
  assetId: string;
  objectKey: string;
  spaceId: string;
  sub: string;
  avatar?: boolean;
}

export interface AssetVerificationRecord {
  provider: StorageProviderName;
  expectedSize: number;
  mimeType: string;
}

export function validateStoredObject(
  asset: AssetVerificationRecord,
  stat: StoredObjectInfo,
) {
  if (stat.sizeBytes !== asset.expectedSize) {
    throw badRequest('SIZE_MISMATCH', '存储对象大小与申请不一致');
  }
  if (asset.provider === 'qiniu' && stat.mimeType !== asset.mimeType) {
    throw badRequest('MIME_MISMATCH', '存储对象 MIME 与申请不一致');
  }
}

export function registerAssets(app: FastifyInstance, context: AppContext, auth: AuthHandler) {
  const { db, config } = context;
  const provider = createStorageProvider(config);

  app.post('/api/media-assets/token', {
    preHandler: auth,
    config: { rateLimit: { max: 30, timeWindow: '1 minute' } },
  }, async (request, reply) => {
    const input = tokenSchema.parse(request.body);
    if (input.purpose === 'avatar' && !avatarMime.has(input.mimeType)) {
      throw badRequest('INVALID_AVATAR_TYPE', '头像仅支持图片');
    }
    const spaceId = input.purpose === 'avatar'
      ? await personalSpaceId(context, request.user.id)
      : await activeSpaceId(context, request.user.id);
    const assetId = randomUUID();
    const objectKey = buildObjectKey(spaceId, input.fileName, input.mimeType);
    const localUploadToken = provider.name === 'local'
      ? jwt.sign(
        { type: 'upload', assetId, objectKey, spaceId, sub: request.user.id },
        config.storage.signingSecret,
        { expiresIn: config.storage.tokenTtlSeconds },
      )
      : undefined;
    await db.query(
      `insert into media_assets(id, space_id, owner_id, provider, bucket, object_key, mime_type, expected_size)
       values ($1, $2, $3, $4, $5, $6, $7, $8)`,
      [assetId, spaceId, request.user.id, provider.name, provider.bucket, objectKey, input.mimeType, input.sizeBytes],
    );
    const grant = await provider.createUploadGrant({
      assetId,
      objectKey,
      mimeType: input.mimeType,
      sizeBytes: input.sizeBytes,
      localUploadToken,
    });
    return reply.code(201).send({ assetId, ...grant });
  });

  app.post('/api/media-assets/local-upload', async (request) => {
    if (provider.name !== 'local') throw notFound('本地上传接口未启用');
    const token = bearer(request.headers.authorization);
    const claims = verifyStorageToken(context, token, 'upload');
    const asset = await db.query<{ status: string; expected_size: string; mime_type: string }>(
      `select status, expected_size, mime_type from media_assets
       where id = $1 and space_id = $2 and owner_id = $3 and object_key = $4 and provider = 'local'`,
      [claims.assetId, claims.spaceId, claims.sub, claims.objectKey],
    );
    if (!asset.rows[0] || asset.rows[0].status !== 'pending') throw forbidden();
    const part = await request.file();
    if (!part) throw badRequest('FILE_REQUIRED', '缺少上传文件');
    if (part.mimetype !== asset.rows[0].mime_type) throw badRequest('MIME_MISMATCH', '文件类型不匹配');
    const target = resolveLocalObjectPath(config.storage.dir, claims.objectKey);
    await fs.mkdir(path.dirname(target), { recursive: true });
    try {
      await pipeline(part.file, createWriteStream(target, { flags: 'wx' }));
      const stat = await fs.stat(target);
      if (stat.size !== Number(asset.rows[0].expected_size)) {
        await fs.unlink(target);
        throw badRequest('SIZE_MISMATCH', '文件大小与申请不一致');
      }
      return { ok: true, assetId: claims.assetId, sizeBytes: stat.size };
    } catch (error) {
      await fs.rm(target, { force: true });
      throw error;
    }
  });

  app.post('/api/media-assets/complete', { preHandler: auth }, async (request) => {
    const { assetId } = assetIdSchema.parse(request.body);
    const spaceId = await activeSpaceId(context, request.user.id);
    const asset = await db.query<{
      object_key: string; expected_size: string; mime_type: string;
      status: string; provider: 'local' | 'qiniu'; bucket: string | null;
    }>(
      `select object_key, expected_size, mime_type, status, provider, bucket from media_assets
       where id = $1 and space_id = $2 and owner_id = $3`,
      [assetId, spaceId, request.user.id],
    );
    const row = asset.rows[0];
    if (!row) throw notFound('媒体资产不存在');
    assertObjectKeyBelongsToSpace(row.object_key, spaceId);
    if (row.provider !== provider.name || row.bucket !== provider.bucket) {
      throw conflict('STORAGE_PROVIDER_MISMATCH', '资产所属存储 provider 当前不可用');
    }
    let stat;
    try {
      stat = await provider.statObject(row.object_key);
    } catch (error) {
      request.log.warn({ err: error, assetId, provider: row.provider }, 'storage object verification failed');
      throw new AppError(502, 'STORAGE_VERIFY_FAILED', '无法验证远端文件，请稍后重试');
    }
    validateStoredObject({
      provider: row.provider,
      expectedSize: Number(row.expected_size),
      mimeType: row.mime_type,
    }, stat);
    const updated = await db.query(
      `update media_assets set status = 'ready', size_bytes = $2, object_hash = $3, completed_at = now()
       where id = $1 and status = 'pending' returning id`,
      [assetId, stat.sizeBytes, stat.hash ?? null],
    );
    if (!updated.rowCount && row.status !== 'ready') throw conflict('ASSET_STATE_CHANGED', '资产状态已变化');
    return { assetId, provider: row.provider, status: 'ready', sizeBytes: stat.sizeBytes };
  });

  app.post('/api/media-assets/:assetId/sign', { preHandler: auth }, async (request) => {
    const { assetId } = assetIdSchema.parse(request.params);
    const { variant } = signSchema.parse(request.body ?? {});
    const spaceId = await activeSpaceId(context, request.user.id);
    const asset = await db.query<{
      object_key: string; mime_type: string; provider: 'local' | 'qiniu'; bucket: string | null;
    }>(
      `select object_key, mime_type, provider, bucket from media_assets
       where id = $1 and space_id = $2 and status = 'ready'`,
      [assetId, spaceId],
    );
    const row = asset.rows[0];
    if (!row) throw notFound('媒体资产不存在');
    assertObjectKeyBelongsToSpace(row.object_key, spaceId);
    if (row.provider !== provider.name || row.bucket !== provider.bucket) {
      throw conflict('STORAGE_PROVIDER_MISMATCH', '资产所属存储 provider 当前不可用');
    }
    if (provider.name === 'qiniu') {
      const signed = await provider.signDownload(row.object_key, row.mime_type, variant as DownloadVariant);
      return { ...signed, provider: provider.name, variant };
    }
    const signed = jwt.sign(
      { type: 'download', assetId, objectKey: row.object_key, spaceId, sub: request.user.id },
      config.storage.signingSecret,
      { expiresIn: config.storage.tokenTtlSeconds },
    );
    return {
      url: `${config.publicBaseUrl}/private-media/${assetId}?token=${encodeURIComponent(signed)}`,
      expiresIn: config.storage.tokenTtlSeconds,
      provider: provider.name,
      variant: 'original',
    };
  });

  app.get('/private-media/:assetId', async (request, reply) => {
    if (provider.name !== 'local') throw notFound('本地媒体读取接口未启用');
    const { assetId } = assetIdSchema.parse(request.params);
    const { token } = z.object({ token: z.string().min(1) }).strict().parse(request.query);
    const claims = verifyStorageToken(context, token, 'download');
    if (claims.assetId !== assetId) throw forbidden();
    const asset = await db.query<{ object_key: string; mime_type: string }>(
      `select a.object_key, a.mime_type from media_assets a
       where a.id = $1 and a.space_id = $2 and a.object_key = $3
         and a.provider = 'local' and a.status = 'ready'
         and (
           exists (
             select 1 from couple_members m
             where m.space_id = a.space_id and m.user_id = $4 and m.status = 'active'
           )
           or (
             $5::boolean
             and exists (
               select 1 from users u
               where u.avatar_asset_id = a.id
                 and (
                   u.id = $4
                   or exists (
                     select 1 from couple_links cl
                     where cl.status = 'active'
                       and ((cl.user_a_id = u.id and cl.user_b_id = $4)
                         or (cl.user_b_id = u.id and cl.user_a_id = $4))
                   )
                 )
             )
           )
         )`,
      [assetId, claims.spaceId, claims.objectKey, claims.sub, Boolean(claims.avatar)],
    );
    const row = asset.rows[0];
    if (!row) throw forbidden();
    const target = resolveLocalObjectPath(config.storage.dir, row.object_key);
    await fs.access(target);
    return reply.type(row.mime_type).header('Cache-Control', 'private, max-age=300').send(createReadStream(target));
  });
}

function bearer(header?: string) {
  if (!header?.startsWith('Bearer ')) throw forbidden('缺少上传令牌');
  return header.slice(7);
}

function verifyStorageToken(context: AppContext, token: string, type: StorageClaims['type']) {
  try {
    const claims = jwt.verify(token, context.config.storage.signingSecret) as StorageClaims;
    if (claims.type !== type || !claims.assetId || !claims.objectKey || !claims.spaceId || !claims.sub) throw new Error();
    assertObjectKeyBelongsToSpace(claims.objectKey, claims.spaceId);
    resolveLocalObjectPath(context.config.storage.dir, claims.objectKey);
    return claims;
  } catch {
    throw forbidden('媒体令牌无效或已过期');
  }
}
