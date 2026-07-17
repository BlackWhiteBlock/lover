import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import type { AppContext, AuthHandler } from '../types.js';
import { getActiveCoupleLink, partnerIdFromLink, type CoupleLinkRow } from './spaces.js';

export type PartnerActivityType =
  | 'letter_instant'
  | 'letter_capsule'
  | 'media_created'
  | 'anniversary_created';

type EmitInput = {
  actorId: string;
  actorNickname: string;
  type: PartnerActivityType;
  entityType: 'letter' | 'media' | 'anniversary';
  entityId: string;
  title: string;
  payload?: Record<string, unknown>;
  link?: CoupleLinkRow | null;
};

/** Write one inbox row for the bound partner. No-op when unlinked. */
export async function emitPartnerActivity(context: AppContext, input: EmitInput) {
  const link = input.link === undefined
    ? await getActiveCoupleLink(context, input.actorId)
    : input.link;
  if (!link) return;
  const recipientId = partnerIdFromLink(link, input.actorId);
  await context.db.query(
    `insert into partner_activity_events(
       couple_link_id, actor_id, recipient_id, type, entity_type, entity_id, title, payload
     ) values ($1, $2, $3, $4, $5, $6, $7, $8::jsonb)
     on conflict (recipient_id, type, entity_id) do nothing`,
    [
      link.id,
      input.actorId,
      recipientId,
      input.type,
      input.entityType,
      input.entityId,
      input.title.slice(0, 160),
      JSON.stringify(input.payload ?? {}),
    ],
  );
}

export function registerActivity(app: FastifyInstance, context: AppContext, auth: AuthHandler) {
  const { db } = context;

  app.get('/api/activity', { preHandler: auth }, async (request) => {
    const query = z.object({
      unreadOnly: z.coerce.boolean().optional().default(true),
      limit: z.coerce.number().int().min(1).max(50).default(20),
    }).strict().parse(request.query ?? {});

    const result = await db.query<{
      id: string;
      type: PartnerActivityType;
      entityType: string;
      entityId: string;
      title: string;
      payload: unknown;
      actorNickname: string;
      createdAt: Date;
      readAt: Date | null;
    }>(
      `select e.id, e.type, e.entity_type as "entityType", e.entity_id as "entityId",
              e.title, e.payload, u.nickname as "actorNickname",
              e.created_at as "createdAt", e.read_at as "readAt"
       from partner_activity_events e
       join users u on u.id = e.actor_id
       where e.recipient_id = $1
         and ($2::boolean = false or e.read_at is null)
       order by e.created_at desc
       limit $3`,
      [request.user.id, query.unreadOnly, query.limit],
    );

    const unread = await db.query<{ count: string }>(
      `select count(*)::text as count from partner_activity_events
       where recipient_id = $1 and read_at is null`,
      [request.user.id],
    );

    return {
      items: result.rows.map((row) => ({
        id: row.id,
        type: row.type,
        entityType: row.entityType,
        entityId: row.entityId,
        title: row.title,
        payload: row.payload ?? {},
        actorNickname: row.actorNickname,
        createdAt: row.createdAt,
        readAt: row.readAt,
      })),
      unreadCount: Number(unread.rows[0]?.count ?? 0),
    };
  });

  app.post('/api/activity/read', { preHandler: auth }, async (request) => {
    const body = z.object({
      ids: z.array(z.string().uuid()).max(100).optional(),
      all: z.boolean().optional(),
    }).strict().refine(
      (value) => value.all === true || (value.ids != null && value.ids.length > 0),
      '请指定 ids 或 all=true',
    ).parse(request.body ?? {});

    let result;
    if (body.all) {
      result = await db.query(
        `update partner_activity_events
         set read_at = now()
         where recipient_id = $1 and read_at is null`,
        [request.user.id],
      );
    } else {
      result = await db.query(
        `update partner_activity_events
         set read_at = now()
         where recipient_id = $1 and id = any($2::uuid[]) and read_at is null`,
        [request.user.id, body.ids],
      );
    }
    return { ok: true, marked: result.rowCount ?? 0 };
  });
}
