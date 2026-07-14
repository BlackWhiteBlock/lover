import { createReadStream, createWriteStream } from 'node:fs';
import fs from 'node:fs/promises';
import path from 'node:path';
import { pipeline } from 'node:stream/promises';
import { randomBytes, randomUUID } from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import jwt from 'jsonwebtoken';
import { z } from 'zod';
import type { AppContext, AuthHandler } from '../types.js';
import { badRequest, forbidden, notFound } from '../errors.js';
import { activeSpaceId } from './couples.js';
import { resolveLocalObjectPath } from '../storage/local.js';

const allowedMime = new Set([
  'image/jpeg', 'image/png', 'image/webp', 'image/heic',
  'video/mp4', 'video/quicktime',
]);
const tokenSchema = z.object({
  fileName: z.string().trim().min(1).max(200),
  mimeType: z.string().refine((value) => allowedMime.has(value), '不支持的媒体类型'),
  sizeBytes: z.number().int().positive().max(300 * 1024 * 1024),
}).strict();
const assetIdSchema = z.object({ assetId: z.string().uuid() }).strict();

interface StorageClaims extends jwt.JwtPayload {
  type: 'upload' | 'download';
  assetId: string;
  objectKey: string;
  spaceId: string;
  sub: string;
}

export function registerAssets(app: FastifyInstance, context: AppContext, auth: AuthHandler) {
  const { db, config } = context;

  app.post('/api/media-assets/token', { preHandler: auth }, async (request, reply) => {
    const spaceId = await activeSpaceId(context, request.user.id);
    const input = tokenSchema.parse(request.body);
    const assetId = randomUUID();
    const ext = extension(input.fileName, input.mimeType);
    const objectKey = `couples/${spaceId}/${new Date().toISOString().slice(0, 7)}/${randomBytes(12).toString('hex')}.${ext}`;
    const uploadToken = jwt.sign(
      { type: 'upload', assetId, objectKey, spaceId, sub: request.user.id },
      config.storage.signingSecret,
      { expiresIn: config.storage.tokenTtlSeconds },
    );
    await db.query(
      `insert into media_assets(id, space_id, owner_id, provider, object_key, mime_type, expected_size)
       values ($1, $2, $3, 'local', $4, $5, $6)`,
      [assetId, spaceId, request.user.id, objectKey, input.mimeType, input.sizeBytes],
    );
    return reply.code(201).send({
      assetId,
      provider: 'local',
      uploadToken,
      uploadUrl: `${config.publicBaseUrl}/api/media-assets/local-upload`,
      expiresIn: config.storage.tokenTtlSeconds,
    });
  });

  app.post('/api/media-assets/local-upload', async (request) => {
    const token = bearer(request.headers.authorization);
    const claims = verifyStorageToken(context, token, 'upload');
    const asset = await db.query<{ status: string; expected_size: string; mime_type: string }>(
      `select status, expected_size, mime_type from media_assets
       where id = $1 and space_id = $2 and owner_id = $3 and object_key = $4`,
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
    const asset = await db.query<{ object_key: string; expected_size: string; status: string }>(
      `select object_key, expected_size, status from media_assets
       where id = $1 and space_id = $2 and owner_id = $3`,
      [assetId, spaceId, request.user.id],
    );
    const row = asset.rows[0];
    if (!row) throw notFound('媒体资产不存在');
    const stat = await fs.stat(resolveLocalObjectPath(config.storage.dir, row.object_key)).catch(() => null);
    if (!stat || stat.size !== Number(row.expected_size)) throw badRequest('UPLOAD_INCOMPLETE', '文件尚未完整上传');
    await db.query(
      `update media_assets set status = 'ready', size_bytes = $2, completed_at = now() where id = $1`,
      [assetId, stat.size],
    );
    return { assetId, status: 'ready', sizeBytes: stat.size };
  });

  app.post('/api/media-assets/:assetId/sign', { preHandler: auth }, async (request) => {
    const { assetId } = assetIdSchema.parse(request.params);
    const spaceId = await activeSpaceId(context, request.user.id);
    const asset = await db.query<{ object_key: string }>(
      `select object_key from media_assets where id = $1 and space_id = $2 and status = 'ready'`,
      [assetId, spaceId],
    );
    const row = asset.rows[0];
    if (!row) throw notFound('媒体资产不存在');
    const signed = jwt.sign(
      { type: 'download', assetId, objectKey: row.object_key, spaceId, sub: request.user.id },
      config.storage.signingSecret,
      { expiresIn: config.storage.tokenTtlSeconds },
    );
    return {
      url: `${config.publicBaseUrl}/private-media/${assetId}?token=${encodeURIComponent(signed)}`,
      expiresIn: config.storage.tokenTtlSeconds,
    };
  });

  app.get('/private-media/:assetId', async (request, reply) => {
    const { assetId } = assetIdSchema.parse(request.params);
    const { token } = z.object({ token: z.string().min(1) }).strict().parse(request.query);
    const claims = verifyStorageToken(context, token, 'download');
    if (claims.assetId !== assetId) throw forbidden();
    const asset = await db.query<{ object_key: string; mime_type: string }>(
      `select a.object_key, a.mime_type from media_assets a
       join couple_members m on m.space_id = a.space_id
       where a.id = $1 and a.space_id = $2 and a.object_key = $3
         and a.status = 'ready' and m.user_id = $4 and m.status = 'active'`,
      [assetId, claims.spaceId, claims.objectKey, claims.sub],
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
    resolveLocalObjectPath(context.config.storage.dir, claims.objectKey);
    return claims;
  } catch {
    throw forbidden('媒体令牌无效或已过期');
  }
}

function extension(name: string, mime: string) {
  const supplied = name.split('.').pop()?.toLowerCase();
  const allowed: Record<string, string[]> = {
    'image/jpeg': ['jpg', 'jpeg'],
    'image/png': ['png'],
    'image/webp': ['webp'],
    'image/heic': ['heic'],
    'video/mp4': ['mp4'],
    'video/quicktime': ['mov'],
  };
  return supplied && allowed[mime]?.includes(supplied) ? supplied : allowed[mime]![0]!;
}
