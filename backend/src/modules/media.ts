import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import type { AppContext, AuthHandler } from '../types.js';
import { badRequest, notFound } from '../errors.js';
import { readableSpaceIds, writeSpaceId } from './spaces.js';

const dateSchema = z.string().regex(/^\d{4}-\d{2}-\d{2}$/);
const mediaAssetPart = z.object({
  type: z.enum(['image', 'video']),
  assetId: z.string().uuid(),
  thumbnailAssetId: z.string().uuid().nullable().optional(),
}).strict().superRefine((value, ctx) => {
  if (value.type === 'video' && !value.thumbnailAssetId) {
    ctx.addIssue({ code: 'custom', path: ['thumbnailAssetId'], message: '视频必须提供封面资产' });
  }
});
const mediaInput = z.object({
  caption: z.string().trim().max(200).default(''),
  mediaDate: dateSchema,
  assets: z.array(mediaAssetPart).min(1).max(9),
}).strict();
const mediaUpdate = z.object({
  caption: z.string().trim().max(200).optional(),
  mediaDate: dateSchema.optional(),
}).strict().refine((value) => Object.keys(value).length > 0);
const idParams = z.object({ id: z.string().uuid() }).strict();
const pageQuery = z.object({
  cursor: z.string().datetime({ offset: true }).optional(),
  limit: z.coerce.number().int().min(1).max(100).default(30),
}).strict();

type MediaRow = {
  id: string;
  caption: string;
  mediaDate: string;
  uploaderId: string;
  createdAt: Date | string;
};

type MediaAssetRow = {
  id: string;
  mediaItemId: string;
  sortOrder: number;
  type: 'image' | 'video';
  assetId: string;
  thumbnailAssetId: string | null;
};

export function registerMedia(app: FastifyInstance, context: AppContext, auth: AuthHandler) {
  const { db } = context;

  app.get('/api/media', { preHandler: auth }, async (request) => {
    const spaceIds = await readableSpaceIds(context, request.user.id);
    const query = pageQuery.parse(request.query);
    const result = await db.query<MediaRow>(
      `select id, caption, media_date as "mediaDate", uploader_id as "uploaderId", created_at as "createdAt"
       from media_items where space_id = any($1::uuid[]) and ($2::timestamptz is null or created_at < $2)
       order by created_at desc limit $3`,
      [spaceIds, query.cursor ?? null, query.limit + 1],
    );
    const hasMore = result.rows.length > query.limit;
    const rows = hasMore ? result.rows.slice(0, query.limit) : result.rows;
    const items = await hydrateMediaItems(context, rows);
    return { items, nextCursor: hasMore ? (rows.at(-1) as MediaRow).createdAt : null };
  });

  app.get('/api/media/:id', { preHandler: auth }, async (request) => {
    const spaceIds = await readableSpaceIds(context, request.user.id);
    const { id } = idParams.parse(request.params);
    const item = await findMedia(context, spaceIds, id);
    if (!item) throw notFound('媒体记录不存在');
    return item;
  });

  app.post('/api/media', { preHandler: auth }, async (request, reply) => {
    const target = await writeSpaceId(context, request.user.id);
    const input = mediaInput.parse(request.body);
    await assertReadyAssets(
      context,
      target.spaceId,
      input.assets.flatMap((part) => [part.assetId, part.thumbnailAssetId]),
    );

    const mediaItem = await db.transaction(async (client) => {
      const inserted = await client.query<MediaRow>(
        `insert into media_items(space_id, uploader_id, caption, media_date, ownership, couple_link_id)
         values ($1, $2, $3, $4, $5, $6)
         returning id, caption, media_date as "mediaDate", uploader_id as "uploaderId", created_at as "createdAt"`,
        [
          target.spaceId,
          request.user.id,
          input.caption,
          input.mediaDate,
          target.ownership,
          target.coupleLinkId,
        ],
      );
      const row = inserted.rows[0]!;
      for (const [index, part] of input.assets.entries()) {
        await client.query(
          `insert into media_item_assets(media_item_id, sort_order, type, asset_id, thumbnail_asset_id)
           values ($1, $2, $3, $4, $5)`,
          [row.id, index, part.type, part.assetId, part.thumbnailAssetId ?? null],
        );
      }
      return row;
    });
    const hydrated = await hydrateMediaItems(context, [mediaItem]);
    return reply.code(201).send(hydrated[0]);
  });

  app.patch('/api/media/:id', { preHandler: auth }, async (request) => {
    const spaceIds = await readableSpaceIds(context, request.user.id);
    const { id } = idParams.parse(request.params);
    const input = mediaUpdate.parse(request.body);
    const current = await findMediaRow(context, spaceIds, id);
    if (!current) throw notFound('媒体记录不存在');
    const caption = input.caption ?? current.caption;
    const mediaDate = input.mediaDate ?? current.mediaDate;
    const result = await db.query<MediaRow>(
      `update media_items set caption = $3, media_date = $4, updated_at = now()
       where id = $1 and space_id = any($2::uuid[]) and uploader_id = $5
       returning id, caption, media_date as "mediaDate", uploader_id as "uploaderId", created_at as "createdAt"`,
      [id, spaceIds, caption, mediaDate, request.user.id],
    );
    if (!result.rowCount) throw notFound('媒体记录不存在或无权编辑');
    const hydrated = await hydrateMediaItems(context, result.rows);
    return hydrated[0];
  });

  app.delete('/api/media/:id', { preHandler: auth }, async (request) => {
    const spaceIds = await readableSpaceIds(context, request.user.id);
    const { id } = idParams.parse(request.params);
    const result = await db.query(
      `delete from media_items where id = $1 and space_id = any($2::uuid[]) and uploader_id = $3`,
      [id, spaceIds, request.user.id],
    );
    if (!result.rowCount) throw notFound('媒体记录不存在或无权删除');
    return { ok: true };
  });
}

export async function listRecentMedia(context: AppContext, spaceIds: string | string[], limit = 6) {
  const ids = Array.isArray(spaceIds) ? spaceIds : [spaceIds];
  const result = await context.db.query<MediaRow>(
    `select id, caption, media_date as "mediaDate", uploader_id as "uploaderId", created_at as "createdAt"
     from media_items where space_id = any($1::uuid[])
     order by created_at desc limit $2`,
    [ids, limit],
  );
  return hydrateMediaItems(context, result.rows);
}

async function findMedia(context: AppContext, spaceIds: string[], id: string) {
  const row = await findMediaRow(context, spaceIds, id);
  if (!row) return null;
  const [item] = await hydrateMediaItems(context, [row]);
  return item ?? null;
}

async function findMediaRow(context: AppContext, spaceIds: string[], id: string) {
  const result = await context.db.query<MediaRow>(
    `select id, caption, media_date as "mediaDate", uploader_id as "uploaderId", created_at as "createdAt"
     from media_items where id = $1 and space_id = any($2::uuid[])`,
    [id, spaceIds],
  );
  return result.rows[0] ?? null;
}

async function hydrateMediaItems(context: AppContext, rows: MediaRow[]) {
  if (!rows.length) return [];
  const ids = rows.map((row) => row.id);
  const assets = await context.db.query<MediaAssetRow>(
    `select id, media_item_id as "mediaItemId", sort_order as "sortOrder", type,
            asset_id as "assetId", thumbnail_asset_id as "thumbnailAssetId"
     from media_item_assets
     where media_item_id = any($1::uuid[])
     order by media_item_id, sort_order`,
    [ids],
  );
  const byItem = new Map<string, MediaAssetRow[]>();
  for (const asset of assets.rows) {
    const list = byItem.get(asset.mediaItemId) ?? [];
    list.push(asset);
    byItem.set(asset.mediaItemId, list);
  }
  return rows.map((row) => {
    const itemAssets = (byItem.get(row.id) ?? []).map((asset) => ({
      id: asset.id,
      type: asset.type,
      assetId: asset.assetId,
      thumbnailAssetId: asset.thumbnailAssetId,
      sortOrder: asset.sortOrder,
    }));
    return {
      id: row.id,
      caption: row.caption,
      mediaDate: row.mediaDate,
      uploaderId: row.uploaderId,
      createdAt: row.createdAt,
      assets: itemAssets,
    };
  });
}

async function assertReadyAssets(context: AppContext, spaceId: string, values: Array<string | null | undefined>) {
  const ids = [...new Set(values.filter((value): value is string => Boolean(value)))];
  const result = await context.db.query<{ id: string }>(
    `select id from media_assets where space_id = $1 and status = 'ready' and id = any($2::uuid[])`,
    [spaceId, ids],
  );
  if (result.rows.length !== ids.length) throw badRequest('ASSET_NOT_READY', '媒体资产不存在、未就绪或不属于当前空间');
}
