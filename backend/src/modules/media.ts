import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import type { AppContext, AuthHandler } from '../types.js';
import { badRequest, notFound } from '../errors.js';
import { activeSpaceId } from './couples.js';

const dateSchema = z.string().regex(/^\d{4}-\d{2}-\d{2}$/);
const mediaBase = z.object({
  type: z.enum(['image', 'video']),
  assetId: z.string().uuid(),
  thumbnailAssetId: z.string().uuid().nullable().optional(),
  caption: z.string().trim().max(200).default(''),
  mediaDate: dateSchema,
}).strict();
const mediaInput = mediaBase.superRefine((value, ctx) => {
  if (value.type === 'video' && !value.thumbnailAssetId) {
    ctx.addIssue({ code: 'custom', path: ['thumbnailAssetId'], message: '视频必须提供封面资产' });
  }
});
const mediaUpdate = mediaBase.partial().strict().refine((value) => Object.keys(value).length > 0);
const idParams = z.object({ id: z.string().uuid() }).strict();
const pageQuery = z.object({
  cursor: z.string().datetime({ offset: true }).optional(),
  limit: z.coerce.number().int().min(1).max(100).default(30),
}).strict();

export function registerMedia(app: FastifyInstance, context: AppContext, auth: AuthHandler) {
  const { db } = context;

  app.get('/api/media', { preHandler: auth }, async (request) => {
    const spaceId = await activeSpaceId(context, request.user.id);
    const query = pageQuery.parse(request.query);
    const result = await db.query(
      `select id, type, asset_id as "assetId", thumbnail_asset_id as "thumbnailAssetId",
              caption, media_date as "mediaDate", uploader_id as "uploaderId", created_at as "createdAt"
       from media_items where space_id = $1 and ($2::timestamptz is null or created_at < $2)
       order by created_at desc limit $3`,
      [spaceId, query.cursor ?? null, query.limit + 1],
    );
    const hasMore = result.rows.length > query.limit;
    const items = hasMore ? result.rows.slice(0, query.limit) : result.rows;
    return { items, nextCursor: hasMore ? (items.at(-1) as { createdAt: Date }).createdAt : null };
  });

  app.get('/api/media/:id', { preHandler: auth }, async (request) => {
    const spaceId = await activeSpaceId(context, request.user.id);
    const { id } = idParams.parse(request.params);
    const item = await findMedia(context, spaceId, id);
    if (!item) throw notFound('媒体记录不存在');
    return item;
  });

  app.post('/api/media', { preHandler: auth }, async (request, reply) => {
    const spaceId = await activeSpaceId(context, request.user.id);
    const input = mediaInput.parse(request.body);
    await assertReadyAssets(context, spaceId, [input.assetId, input.thumbnailAssetId]);
    const result = await db.query(
      `insert into media_items(space_id, uploader_id, type, asset_id, thumbnail_asset_id, caption, media_date)
       values ($1, $2, $3, $4, $5, $6, $7)
       returning id, type, asset_id as "assetId", thumbnail_asset_id as "thumbnailAssetId",
                 caption, media_date as "mediaDate", uploader_id as "uploaderId", created_at as "createdAt"`,
      [spaceId, request.user.id, input.type, input.assetId, input.thumbnailAssetId ?? null, input.caption, input.mediaDate],
    );
    return reply.code(201).send(result.rows[0]);
  });

  app.patch('/api/media/:id', { preHandler: auth }, async (request) => {
    const spaceId = await activeSpaceId(context, request.user.id);
    const { id } = idParams.parse(request.params);
    const input = mediaUpdate.parse(request.body);
    const current = await findMedia(context, spaceId, id) as Record<string, unknown> | null;
    if (!current) throw notFound('媒体记录不存在');
    const merged = mediaInput.parse({ ...current, ...input });
    await assertReadyAssets(context, spaceId, [merged.assetId, merged.thumbnailAssetId]);
    const result = await db.query(
      `update media_items set type = $3, asset_id = $4, thumbnail_asset_id = $5,
          caption = $6, media_date = $7, updated_at = now()
       where id = $1 and space_id = $2
       returning id, type, asset_id as "assetId", thumbnail_asset_id as "thumbnailAssetId",
                 caption, media_date as "mediaDate", uploader_id as "uploaderId", created_at as "createdAt"`,
      [id, spaceId, merged.type, merged.assetId, merged.thumbnailAssetId ?? null, merged.caption, merged.mediaDate],
    );
    return result.rows[0];
  });

  app.delete('/api/media/:id', { preHandler: auth }, async (request) => {
    const spaceId = await activeSpaceId(context, request.user.id);
    const { id } = idParams.parse(request.params);
    const result = await db.query(`delete from media_items where id = $1 and space_id = $2`, [id, spaceId]);
    if (!result.rowCount) throw notFound('媒体记录不存在');
    return { ok: true };
  });
}

async function findMedia(context: AppContext, spaceId: string, id: string) {
  const result = await context.db.query(
    `select id, type, asset_id as "assetId", thumbnail_asset_id as "thumbnailAssetId",
            caption, media_date as "mediaDate", uploader_id as "uploaderId", created_at as "createdAt"
     from media_items where id = $1 and space_id = $2`,
    [id, spaceId],
  );
  return result.rows[0] ?? null;
}

async function assertReadyAssets(context: AppContext, spaceId: string, values: Array<string | null | undefined>) {
  const ids = [...new Set(values.filter((value): value is string => Boolean(value)))];
  const result = await context.db.query<{ id: string }>(
    `select id from media_assets where space_id = $1 and status = 'ready' and id = any($2::uuid[])`,
    [spaceId, ids],
  );
  if (result.rows.length !== ids.length) throw badRequest('ASSET_NOT_READY', '媒体资产不存在、未就绪或不属于当前空间');
}
