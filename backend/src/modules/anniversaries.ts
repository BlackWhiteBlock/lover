import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import type { AppContext, AuthHandler } from '../types.js';
import { anniversaryCountdown, formatCalendarDate, parseCalendarDate } from '../domain.js';
import { badRequest, notFound } from '../errors.js';
import { emitPartnerActivity } from './activity.js';
import { getActiveCoupleLink, readableSpaceIds, writeSpaceId } from './spaces.js';

const calendarDate = z.string().refine((value) => {
  try { parseCalendarDate(value); return true; } catch { return false; }
}, '日期无效');
const inputSchema = z.object({
  title: z.string().trim().min(1).max(30),
  date: calendarDate,
  type: z.enum(['yearly', 'milestone']),
  coverAssetId: z.string().uuid().nullable().optional(),
}).strict();
const updateSchema = inputSchema.partial().strict().refine((value) => Object.keys(value).length > 0);
const idSchema = z.object({ id: z.string().uuid() }).strict();

export function registerAnniversaries(app: FastifyInstance, context: AppContext, auth: AuthHandler) {
  const { db } = context;

  app.get('/api/anniversaries', { preHandler: auth }, async (request) => {
    const spaceIds = await readableSpaceIds(context, request.user.id);
    const result = await db.query(
      `select id, title, date, type, cover_asset_id as "coverAssetId",
              created_by as "createdBy", created_at as "createdAt"
       from anniversaries where space_id = any($1::uuid[]) order by date, created_at`,
      [spaceIds],
    );
    return { items: result.rows.map(withCountdown) };
  });

  app.get('/api/anniversaries/:id', { preHandler: auth }, async (request) => {
    const spaceIds = await readableSpaceIds(context, request.user.id);
    const { id } = idSchema.parse(request.params);
    const item = await find(context, spaceIds, id);
    if (!item) throw notFound('纪念日不存在');
    return withCountdown(item);
  });

  app.post('/api/anniversaries', { preHandler: auth }, async (request, reply) => {
    const target = await writeSpaceId(context, request.user.id);
    const input = inputSchema.parse(request.body);
    await assertCover(context, target.spaceId, input.coverAssetId);
    const result = await db.query(
      `insert into anniversaries(space_id, created_by, title, date, type, cover_asset_id, ownership, couple_link_id)
       values ($1, $2, $3, $4, $5, $6, $7, $8)
       returning id, title, date, type, cover_asset_id as "coverAssetId",
                 created_by as "createdBy", created_at as "createdAt"`,
      [
        target.spaceId,
        request.user.id,
        input.title,
        input.date,
        input.type,
        input.coverAssetId ?? null,
        target.ownership,
        target.coupleLinkId,
      ],
    );
    const row = result.rows[0]!;
    const link = await getActiveCoupleLink(context, request.user.id);
    if (link) {
      await emitPartnerActivity(context, {
        actorId: request.user.id,
        actorNickname: request.user.nickname,
        type: 'anniversary_created',
        entityType: 'anniversary',
        entityId: row.id as string,
        title: `${request.user.nickname} 添加了纪念日「${input.title}」`,
        payload: { title: input.title, date: input.date, anniversaryType: input.type },
        link,
      });
    }
    return reply.code(201).send(withCountdown(row));
  });

  app.patch('/api/anniversaries/:id', { preHandler: auth }, async (request) => {
    const spaceIds = await readableSpaceIds(context, request.user.id);
    const { id } = idSchema.parse(request.params);
    const input = updateSchema.parse(request.body);
    const current = await find(context, spaceIds, id);
    if (!current) throw notFound('纪念日不存在');
    const merged = inputSchema.parse({ ...current, ...input });
    await assertCover(context, String(current.space_id ?? spaceIds[0]), merged.coverAssetId);
    const result = await db.query(
      `update anniversaries set title = $3, date = $4, type = $5, cover_asset_id = $6, updated_at = now()
       where id = $1 and space_id = any($2::uuid[]) and created_by = $7
       returning id, title, date, type, cover_asset_id as "coverAssetId",
                 created_by as "createdBy", created_at as "createdAt"`,
      [id, spaceIds, merged.title, merged.date, merged.type, merged.coverAssetId ?? null, request.user.id],
    );
    if (!result.rowCount) throw notFound('纪念日不存在或无权编辑');
    return withCountdown(result.rows[0]!);
  });

  app.delete('/api/anniversaries/:id', { preHandler: auth }, async (request) => {
    const spaceIds = await readableSpaceIds(context, request.user.id);
    const { id } = idSchema.parse(request.params);
    const result = await db.query(
      `delete from anniversaries where id = $1 and space_id = any($2::uuid[]) and created_by = $3`,
      [id, spaceIds, request.user.id],
    );
    if (!result.rowCount) throw notFound('纪念日不存在或无权删除');
    return { ok: true };
  });
}

function withCountdown(row: Record<string, unknown>) {
  const today = formatCalendarDate(new Date());
  return {
    ...row,
    countdown: anniversaryCountdown(row.type as 'yearly' | 'milestone', String(row.date), today),
  };
}

async function find(context: AppContext, spaceIds: string[], id: string) {
  const result = await context.db.query(
    `select id, title, date, type, cover_asset_id as "coverAssetId",
            created_by as "createdBy", created_at as "createdAt", space_id
     from anniversaries where id = $1 and space_id = any($2::uuid[])`,
    [id, spaceIds],
  );
  return result.rows[0] as Record<string, unknown> | undefined;
}

async function assertCover(context: AppContext, spaceId: string, assetId?: string | null) {
  if (!assetId) return;
  const result = await context.db.query(
    `select 1 from media_assets where id = $1 and space_id = $2 and status = 'ready'`,
    [assetId, spaceId],
  );
  if (!result.rowCount) throw badRequest('ASSET_NOT_READY', '封面资产不存在、未就绪或不属于当前空间');
}
