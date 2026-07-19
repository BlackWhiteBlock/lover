import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import type { AppContext, AuthHandler } from '../types.js';
import { badRequest, forbidden, notFound } from '../errors.js';
import { emitPartnerActivity } from './activity.js';
import { getActiveCoupleLink, readableSpaceIds, writeSpaceId } from './spaces.js';

const MAX_MEDIA_ASSETS = 20;

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
const assetOrderKeep = z.object({ partId: z.string().uuid() }).strict();
const assetOrderItem = z.union([assetOrderKeep, mediaAssetPart]);

const mediaInput = z.object({
  caption: z.string().trim().max(200).default(''),
  mediaDate: dateSchema,
  assets: z.array(mediaAssetPart).min(1).max(MAX_MEDIA_ASSETS),
}).strict();
const mediaUpdate = z.object({
  caption: z.string().trim().max(200).optional(),
  mediaDate: dateSchema.optional(),
  addAssets: z.array(mediaAssetPart).max(MAX_MEDIA_ASSETS).optional(),
  removeAssetPartIds: z.array(z.string().uuid()).optional(),
  /** 最终媒体顺序：保留用 partId，新增用 asset 字段；提供时以该列表为准 */
  assetOrder: z.array(assetOrderItem).min(1).max(MAX_MEDIA_ASSETS).optional(),
}).strict().refine(
  (value) => Object.keys(value).length > 0,
  '至少提供一个更新字段',
);
const idParams = z.object({ id: z.string().uuid() }).strict();
/** cursor: `${mediaDate}|${createdAt ISO}`，兼容旧版纯 createdAt ISO */
const pageQuery = z.object({
  cursor: z.string().min(1).optional(),
  limit: z.coerce.number().int().min(1).max(100).default(30),
  year: z.coerce.number().int().min(1970).max(2100).optional(),
}).strict();

const unreadPageQuery = z.object({
  cursor: z.string().min(1).optional(),
  limit: z.coerce.number().int().min(1).max(100).default(30),
}).strict();

type MediaRow = {
  id: string;
  caption: string;
  mediaDate: string;
  uploaderId: string;
  createdAt: Date | string;
  updatedAt: Date | string;
};

type MediaAssetRow = {
  id: string;
  mediaItemId: string;
  sortOrder: number;
  type: 'image' | 'video';
  assetId: string;
  thumbnailAssetId: string | null;
};

function formatMediaDate(value: string | Date): string {
  if (typeof value === 'string') return value.slice(0, 10);
  return value.toISOString().slice(0, 10);
}

function formatCreatedAt(value: string | Date): string {
  if (typeof value === 'string') return value;
  return value.toISOString();
}

function encodeMediaCursor(row: MediaRow): string {
  return `${formatMediaDate(row.mediaDate)}|${formatCreatedAt(row.createdAt)}`;
}

function parseMediaCursor(cursor: string | undefined): { mediaDate: string; createdAt: string } | null {
  if (!cursor) return null;
  const sep = cursor.indexOf('|');
  if (sep > 0) {
    return { mediaDate: cursor.slice(0, sep), createdAt: cursor.slice(sep + 1) };
  }
  // 旧客户端：仅 createdAt，退化为按创建时间翻页
  return { mediaDate: '9999-12-31', createdAt: cursor };
}

export function registerMedia(app: FastifyInstance, context: AppContext, auth: AuthHandler) {
  const { db } = context;

  app.get('/api/media', { preHandler: auth }, async (request) => {
    const spaceIds = await readableSpaceIds(context, request.user.id);
    const query = pageQuery.parse(request.query);
    const cursor = parseMediaCursor(query.cursor);
    const result = await db.query<MediaRow>(
      `select id, caption, media_date as "mediaDate", uploader_id as "uploaderId",
              created_at as "createdAt", updated_at as "updatedAt"
       from media_items
       where space_id = any($1::uuid[])
         and ($4::int is null or extract(year from media_date)::int = $4)
         and (
           $2::date is null
           or (media_date, created_at) < ($2::date, $3::timestamptz)
         )
       order by media_date desc, created_at desc
       limit $5`,
      [
        spaceIds,
        cursor?.mediaDate ?? null,
        cursor?.createdAt ?? null,
        query.year ?? null,
        query.limit + 1,
      ],
    );
    const hasMore = result.rows.length > query.limit;
    const rows = hasMore ? result.rows.slice(0, query.limit) : result.rows;
    const items = await hydrateMediaItems(context, rows);
    return {
      items,
      nextCursor: hasMore ? encodeMediaCursor(rows.at(-1) as MediaRow) : null,
    };
  });

  /** 有时光记录的年份列表（降序），供筛选芯片 */
  app.get('/api/media/years', { preHandler: auth }, async (request) => {
    const spaceIds = await readableSpaceIds(context, request.user.id);
    const result = await db.query<{ year: number }>(
      `select distinct extract(year from media_date)::int as year
       from media_items
       where space_id = any($1::uuid[])
       order by year desc`,
      [spaceIds],
    );
    return { years: result.rows.map((row) => row.year) };
  });

  /** 未读摘要：首次访问建立基线（历史不算未读） */
  app.get('/api/media/unread-summary', { preHandler: auth }, async (request) => {
    await ensureMediaUnreadBaseline(context, request.user.id);
    const spaceIds = await readableSpaceIds(context, request.user.id);
    const result = await db.query<{ count: string }>(
      `select count(*)::text as count
       from media_items m
       join media_unread_baselines b on b.user_id = $1
       where m.space_id = any($2::uuid[])
         and m.uploader_id <> $1
         and greatest(m.created_at, m.updated_at) > b.baseline_at
         and not exists (
           select 1 from media_item_reads r
           where r.user_id = $1
             and r.media_item_id = m.id
             and r.read_at >= greatest(m.created_at, m.updated_at)
         )`,
      [request.user.id, spaceIds],
    );
    const count = Number(result.rows[0]?.count ?? 0);
    return { count, hasUnread: count > 0 };
  });

  app.get('/api/media/unread', { preHandler: auth }, async (request) => {
    await ensureMediaUnreadBaseline(context, request.user.id);
    const spaceIds = await readableSpaceIds(context, request.user.id);
    const query = unreadPageQuery.parse(request.query);
    const offset = query.cursor ? Number(query.cursor) : 0;
    const safeOffset = Number.isFinite(offset) && offset >= 0 ? Math.floor(offset) : 0;
    const result = await db.query<MediaRow>(
      `select m.id, m.caption, m.media_date as "mediaDate", m.uploader_id as "uploaderId",
              m.created_at as "createdAt", m.updated_at as "updatedAt"
       from media_items m
       join media_unread_baselines b on b.user_id = $1
       where m.space_id = any($2::uuid[])
         and m.uploader_id <> $1
         and greatest(m.created_at, m.updated_at) > b.baseline_at
         and not exists (
           select 1 from media_item_reads r
           where r.user_id = $1
             and r.media_item_id = m.id
             and r.read_at >= greatest(m.created_at, m.updated_at)
         )
       order by greatest(m.created_at, m.updated_at) desc, m.id desc
       limit $3 offset $4`,
      [request.user.id, spaceIds, query.limit + 1, safeOffset],
    );
    const hasMore = result.rows.length > query.limit;
    const rows = hasMore ? result.rows.slice(0, query.limit) : result.rows;
    const items = await hydrateMediaItems(context, rows);
    return {
      items,
      nextCursor: hasMore ? String(safeOffset + rows.length) : null,
      count: await countUnreadMedia(context, request.user.id, spaceIds),
    };
  });

  app.post('/api/media/:id/read', { preHandler: auth }, async (request) => {
    const spaceIds = await readableSpaceIds(context, request.user.id);
    const { id } = idParams.parse(request.params);
    const row = await findMediaRow(context, spaceIds, id);
    if (!row) throw notFound('媒体记录不存在');
    await ensureMediaUnreadBaseline(context, request.user.id);
    await db.query(
      `insert into media_item_reads(user_id, media_item_id, read_at)
       values ($1, $2, now())
       on conflict (user_id, media_item_id)
       do update set read_at = excluded.read_at`,
      [request.user.id, id],
    );
    const count = await countUnreadMedia(context, request.user.id, spaceIds);
    return { ok: true, count, hasUnread: count > 0 };
  });

  app.post('/api/media/read', { preHandler: auth }, async (request) => {
    const body = z.object({
      ids: z.array(z.string().uuid()).max(100).optional(),
      all: z.boolean().optional(),
    }).strict().refine(
      (value) => value.all === true || (value.ids != null && value.ids.length > 0),
      '请指定 ids 或 all=true',
    ).parse(request.body ?? {});
    await ensureMediaUnreadBaseline(context, request.user.id);
    const spaceIds = await readableSpaceIds(context, request.user.id);
    if (body.all) {
      await db.query(
        `insert into media_item_reads(user_id, media_item_id, read_at)
         select $1, m.id, now()
         from media_items m
         join media_unread_baselines b on b.user_id = $1
         where m.space_id = any($2::uuid[])
           and m.uploader_id <> $1
           and greatest(m.created_at, m.updated_at) > b.baseline_at
         on conflict (user_id, media_item_id)
         do update set read_at = excluded.read_at`,
        [request.user.id, spaceIds],
      );
    } else {
      await db.query(
        `insert into media_item_reads(user_id, media_item_id, read_at)
         select $1, m.id, now()
         from media_items m
         where m.space_id = any($2::uuid[])
           and m.id = any($3::uuid[])
         on conflict (user_id, media_item_id)
         do update set read_at = excluded.read_at`,
        [request.user.id, spaceIds, body.ids],
      );
    }
    const count = await countUnreadMedia(context, request.user.id, spaceIds);
    return { ok: true, count, hasUnread: count > 0 };
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
         returning id, caption, media_date as "mediaDate", uploader_id as "uploaderId",
                   created_at as "createdAt", updated_at as "updatedAt"`,
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
    const link = await getActiveCoupleLink(context, request.user.id);
    if (link) {
      await emitPartnerActivity(context, {
        actorId: request.user.id,
        actorNickname: request.user.nickname,
        type: 'media_created',
        entityType: 'media',
        entityId: mediaItem.id,
        title: `${request.user.nickname} 存下了一段时光`,
        payload: { caption: input.caption, mediaDate: input.mediaDate },
        link,
      });
    }
    return reply.code(201).send(hydrated[0]);
  });

  const patchMediaHandler = async (request: { user: { id: string }; params: unknown; body: unknown }) => {
    const spaceIds = await readableSpaceIds(context, request.user.id);
    const { id } = idParams.parse(request.params);
    const input = mediaUpdate.parse(request.body);
    const current = await findMediaRow(context, spaceIds, id);
    if (!current) throw notFound('媒体记录不存在');
    const isUploader = current.uploaderId === request.user.id;
    const mutatesAssets = (input.addAssets?.length ?? 0) > 0
      || (input.removeAssetPartIds?.length ?? 0) > 0
      || (input.assetOrder?.length ?? 0) > 0;
    const needsUploader = input.mediaDate !== undefined || mutatesAssets;
    if (needsUploader && !isUploader) {
      throw forbidden('仅创建者可修改日期或增删照片/视频');
    }

    const target = await writeSpaceId(context, request.user.id);

    await db.transaction(async (client) => {
      if (input.caption !== undefined) {
        await client.query(
          `update media_items set caption = $2, updated_at = now() where id = $1`,
          [id, input.caption],
        );
      }
      if (input.mediaDate !== undefined) {
        await client.query(
          `update media_items set media_date = $2, updated_at = now()
           where id = $1 and uploader_id = $3`,
          [id, input.mediaDate, request.user.id],
        );
      }

      if (input.assetOrder?.length) {
        await applyAssetOrder(context, client, {
          mediaItemId: id,
          spaceId: target.spaceId,
          order: input.assetOrder,
        });
        await client.query(
          `update media_items set updated_at = now() where id = $1`,
          [id],
        );
        return;
      }

      if (input.removeAssetPartIds?.length) {
        const existing = await client.query<{ id: string }>(
          `select id from media_item_assets where media_item_id = $1`,
          [id],
        );
        const allowed = new Set(existing.rows.map((row) => row.id));
        for (const partId of input.removeAssetPartIds) {
          if (!allowed.has(partId)) throw badRequest('INVALID_ASSET_PART', '媒体片段不存在');
        }
        const remaining = existing.rows.length - input.removeAssetPartIds.length
          + (input.addAssets?.length ?? 0);
        if (remaining < 1) {
          throw badRequest('MIN_ASSETS', '至少需要保留一张照片或视频');
        }
        await client.query(
          `delete from media_item_assets
           where media_item_id = $1 and id = any($2::uuid[])`,
          [id, input.removeAssetPartIds],
        );
        await client.query(
          `update media_items set updated_at = now() where id = $1`,
          [id],
        );
      }
      if (input.addAssets?.length) {
        const count = await client.query<{ count: string }>(
          `select count(*)::text as count from media_item_assets where media_item_id = $1`,
          [id],
        );
        const currentCount = Number(count.rows[0]?.count ?? 0);
        const removed = input.removeAssetPartIds?.length ?? 0;
        const nextCount = currentCount - removed + input.addAssets.length;
        if (nextCount > MAX_MEDIA_ASSETS) {
          throw badRequest('TOO_MANY_ASSETS', `每条时光最多 ${MAX_MEDIA_ASSETS} 个媒体`);
        }
        await assertReadyAssets(
          context,
          target.spaceId,
          input.addAssets.flatMap((part) => [part.assetId, part.thumbnailAssetId]),
        );
        const maxOrder = await client.query<{ max: number | null }>(
          `select max(sort_order) as max from media_item_assets where media_item_id = $1`,
          [id],
        );
        let sortOrder = (maxOrder.rows[0]?.max ?? -1) + 1;
        for (const part of input.addAssets) {
          await client.query(
            `insert into media_item_assets(media_item_id, sort_order, type, asset_id, thumbnail_asset_id)
             values ($1, $2, $3, $4, $5)`,
            [id, sortOrder++, part.type, part.assetId, part.thumbnailAssetId ?? null],
          );
        }
        await client.query(
          `update media_items set updated_at = now() where id = $1`,
          [id],
        );
      }
    });

    const refreshed = await findMediaRow(context, spaceIds, id);
    if (!refreshed) throw notFound('媒体记录不存在');
    const hydrated = await hydrateMediaItems(context, [refreshed]);
    return hydrated[0];
  };
  app.patch('/api/media/:id', { preHandler: auth }, patchMediaHandler);
  app.post('/api/media/:id', { preHandler: auth }, patchMediaHandler);

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

type DbClient = {
  query: AppContext['db']['query'];
};

async function applyAssetOrder(
  context: AppContext,
  client: DbClient,
  args: {
    mediaItemId: string;
    spaceId: string;
    order: Array<{ partId: string } | z.infer<typeof mediaAssetPart>>;
  },
) {
  const existing = await client.query<{ id: string }>(
    `select id from media_item_assets where media_item_id = $1`,
    [args.mediaItemId],
  );
  const existingIds = new Set(existing.rows.map((row) => row.id));
  const keepIds = new Set<string>();
  const newParts: z.infer<typeof mediaAssetPart>[] = [];

  for (const item of args.order) {
    if ('partId' in item) {
      if (!existingIds.has(item.partId)) {
        throw badRequest('INVALID_ASSET_PART', '媒体片段不存在');
      }
      if (keepIds.has(item.partId)) {
        throw badRequest('DUPLICATE_ASSET_PART', '媒体片段重复');
      }
      keepIds.add(item.partId);
    } else {
      newParts.push(item);
    }
  }

  if (args.order.length < 1) {
    throw badRequest('MIN_ASSETS', '至少需要保留一张照片或视频');
  }

  await assertReadyAssets(
    context,
    args.spaceId,
    newParts.flatMap((part) => [part.assetId, part.thumbnailAssetId]),
  );

  const removeIds = [...existingIds].filter((partId) => !keepIds.has(partId));
  if (removeIds.length) {
    await client.query(
      `delete from media_item_assets
       where media_item_id = $1 and id = any($2::uuid[])`,
      [args.mediaItemId, removeIds],
    );
  }

  // 先挪到临时大序号，避免 sort_order 唯一冲突
  await client.query(
    `update media_item_assets
     set sort_order = sort_order + 1000
     where media_item_id = $1`,
    [args.mediaItemId],
  );

  for (const [index, item] of args.order.entries()) {
    if ('partId' in item) {
      await client.query(
        `update media_item_assets
         set sort_order = $3
         where id = $1 and media_item_id = $2`,
        [item.partId, args.mediaItemId, index],
      );
    } else {
      await client.query(
        `insert into media_item_assets(media_item_id, sort_order, type, asset_id, thumbnail_asset_id)
         values ($1, $2, $3, $4, $5)`,
        [args.mediaItemId, index, item.type, item.assetId, item.thumbnailAssetId ?? null],
      );
    }
  }
}

export async function listRecentMedia(context: AppContext, spaceIds: string | string[], limit = 6) {
  const ids = Array.isArray(spaceIds) ? spaceIds : [spaceIds];
  const result = await context.db.query<MediaRow>(
    `select id, caption, media_date as "mediaDate", uploader_id as "uploaderId",
            created_at as "createdAt", updated_at as "updatedAt"
     from media_items where space_id = any($1::uuid[])
     order by media_date desc, created_at desc
     limit $2`,
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
    `select id, caption, media_date as "mediaDate", uploader_id as "uploaderId",
            created_at as "createdAt", updated_at as "updatedAt"
     from media_items where id = $1 and space_id = any($2::uuid[])`,
    [id, spaceIds],
  );
  return result.rows[0] ?? null;
}

async function ensureMediaUnreadBaseline(context: AppContext, userId: string) {
  await context.db.query(
    `insert into media_unread_baselines(user_id, baseline_at)
     values ($1, now())
     on conflict (user_id) do nothing`,
    [userId],
  );
}

async function countUnreadMedia(
  context: AppContext,
  userId: string,
  spaceIds: string[],
): Promise<number> {
  const result = await context.db.query<{ count: string }>(
    `select count(*)::text as count
     from media_items m
     join media_unread_baselines b on b.user_id = $1
     where m.space_id = any($2::uuid[])
       and m.uploader_id <> $1
       and greatest(m.created_at, m.updated_at) > b.baseline_at
       and not exists (
         select 1 from media_item_reads r
         where r.user_id = $1
           and r.media_item_id = m.id
           and r.read_at >= greatest(m.created_at, m.updated_at)
       )`,
    [userId, spaceIds],
  );
  return Number(result.rows[0]?.count ?? 0);
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
      mediaDate: formatMediaDate(row.mediaDate),
      uploaderId: row.uploaderId,
      createdAt: formatCreatedAt(row.createdAt),
      updatedAt: formatCreatedAt(row.updatedAt ?? row.createdAt),
      assets: itemAssets,
    };
  });
}

async function assertReadyAssets(
  context: AppContext,
  spaceId: string,
  assetIds: Array<string | null | undefined>,
) {
  const ids = [...new Set(assetIds.filter((id): id is string => Boolean(id)))];
  if (!ids.length) return;
  const result = await context.db.query<{ id: string }>(
    `select id from media_assets where space_id = $1 and status = 'ready' and id = any($2::uuid[])`,
    [spaceId, ids],
  );
  if (result.rows.length !== ids.length) {
    throw badRequest('ASSET_NOT_READY', '存在未就绪或不属于当前空间的媒体资产');
  }
}
