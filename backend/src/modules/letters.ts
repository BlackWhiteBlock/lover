import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import type { AppContext, AuthHandler } from '../types.js';
import { isLetterUnlocked } from '../domain.js';
import { badRequest, forbidden, notFound } from '../errors.js';
import { emitPartnerActivity } from './activity.js';
import { getActiveCoupleLink } from './spaces.js';
import { readableSpaceIds, writeSpaceId } from './spaces.js';

const base = z.object({
  title: z.string().trim().min(1).max(80),
  content: z.string().trim().min(1).max(20_000),
});
const letterInput = z.discriminatedUnion('type', [
  // 客户端创建即时信时仍会带 unlockAt/unlockOnPartnerBind，需允许并忽略
  base.extend({
    type: z.literal('instant'),
    unlockAt: z.string().datetime({ offset: true }).nullable().optional(),
    unlockOnPartnerBind: z.boolean().optional(),
  }).strict(),
  base.extend({
    type: z.literal('capsule'),
    unlockAt: z.string().datetime({ offset: true }).nullable().optional(),
    unlockOnPartnerBind: z.boolean().optional().default(false),
  }).strict().superRefine((value, ctx) => {
    if (value.unlockOnPartnerBind) return;
    if (!value.unlockAt) {
      ctx.addIssue({ code: 'custom', path: ['unlockAt'], message: '请设置解锁时间，或勾选绑定后开启' });
      return;
    }
    if (new Date(value.unlockAt) <= new Date()) {
      ctx.addIssue({ code: 'custom', path: ['unlockAt'], message: '解锁时间必须晚于当前时间' });
    }
  }),
]);
const idSchema = z.object({ id: z.string().uuid() }).strict();

interface LetterRow {
  id: string;
  senderId: string;
  senderNickname: string;
  title: string;
  content: string;
  type: 'instant' | 'capsule';
  unlockAt: Date | null;
  unlockOnPartnerBind?: boolean;
  createdAt: Date;
  myOpenedAt?: Date | null;
  recipientOpenedAt?: Date | null;
}

export function serializeLetter(row: LetterRow, viewerId: string, now = new Date()) {
  const unlockOnPartnerBind = Boolean(row.unlockOnPartnerBind);
  const unlocked = isLetterUnlocked(row.type, row.unlockAt, now, unlockOnPartnerBind);
  const isMine = row.senderId === viewerId;
  const openedByMe = Boolean(row.myOpenedAt);
  // 接收方：解锁且未拆 → 密封；发送方始终可看正文
  const isOpened = isMine ? true : openedByMe;
  const revealContent = unlocked && (isMine || openedByMe);
  const deliveryStatus = isMine
    ? (row.recipientOpenedAt ? 'read' : 'sent')
    : null;

  return {
    id: row.id,
    senderId: row.senderId,
    senderNickname: row.senderNickname,
    title: row.title,
    type: row.type,
    unlockAt: row.unlockAt,
    unlockOnPartnerBind,
    isUnlocked: unlocked,
    isOpened,
    deliveryStatus,
    createdAt: row.createdAt,
    ...(revealContent ? { content: row.content, summary: row.content.slice(0, 120) } : {}),
  };
}

export function registerLetters(app: FastifyInstance, context: AppContext, auth: AuthHandler) {
  const { db } = context;

  app.get('/api/letters/unread-summary', { preHandler: auth }, async (request) => {
    const spaceIds = await readableSpaceIds(context, request.user.id);
    const count = await countUnreadLetters(context, request.user.id, spaceIds);
    return { count, hasUnread: count > 0 };
  });

  app.get('/api/letters', { preHandler: auth }, async (request) => {
    const spaceIds = await readableSpaceIds(context, request.user.id);
    const result = await db.query<LetterRow>(
      `${selectLetters()}
       where l.space_id = any($2::uuid[])
       order by l.created_at desc`,
      [request.user.id, spaceIds],
    );
    return {
      items: result.rows.map((row) => serializeLetter(row, request.user.id)),
    };
  });

  app.post('/api/letters/:id/open', { preHandler: auth }, async (request) => {
    const spaceIds = await readableSpaceIds(context, request.user.id);
    const { id } = idSchema.parse(request.params);
    const row = await findLetter(context, spaceIds, id, request.user.id);
    if (!row) throw notFound('信件不存在');
    if (row.senderId === request.user.id) {
      return serializeLetter(row, request.user.id);
    }
    const unlocked = isLetterUnlocked(
      row.type,
      row.unlockAt,
      new Date(),
      Boolean(row.unlockOnPartnerBind),
    );
    if (!unlocked) {
      throw badRequest('LETTER_LOCKED', '信件尚未解锁，暂时不能拆开');
    }
    await db.query(
      `insert into letter_item_reads(user_id, letter_id, opened_at)
       values ($1, $2, now())
       on conflict (user_id, letter_id)
       do update set opened_at = letter_item_reads.opened_at`,
      [request.user.id, id],
    );
    const refreshed = await findLetter(context, spaceIds, id, request.user.id);
    return serializeLetter(refreshed!, request.user.id);
  });

  app.get('/api/letters/:id', { preHandler: auth }, async (request) => {
    const spaceIds = await readableSpaceIds(context, request.user.id);
    const { id } = idSchema.parse(request.params);
    const row = await findLetter(context, spaceIds, id, request.user.id);
    if (!row) throw notFound('信件不存在');
    return serializeLetter(row, request.user.id);
  });

  app.post('/api/letters', { preHandler: auth }, async (request, reply) => {
    const linked = await getActiveCoupleLink(context, request.user.id);
    const target = await writeSpaceId(context, request.user.id);
    const input = letterInput.parse(request.body);
    if (input.type === 'instant' && !linked) {
      throw badRequest('INSTANT_REQUIRES_PARTNER', '绑定另一半后才能发送即时信');
    }
    const unlockOnPartnerBind = input.type === 'capsule' && Boolean(input.unlockOnPartnerBind);
    const unlockAt = input.type === 'capsule' && !unlockOnPartnerBind ? input.unlockAt ?? null : null;
    const result = await db.query<LetterRow>(
      `insert into letters(
         space_id, sender_id, title, content, type, unlock_at,
         unlock_on_partner_bind, ownership, couple_link_id
       ) values ($1, $2, $3, $4, $5, $6, $7, $8, $9)
       returning id, sender_id as "senderId", $10::text as "senderNickname",
                 title, content, type, unlock_at as "unlockAt",
                 unlock_on_partner_bind as "unlockOnPartnerBind", created_at as "createdAt"`,
      [
        target.spaceId,
        request.user.id,
        input.title,
        input.content,
        input.type,
        unlockAt,
        unlockOnPartnerBind,
        target.ownership,
        target.coupleLinkId,
        request.user.nickname,
      ],
    );
    const row = result.rows[0]!;
    if (linked) {
      await emitPartnerActivity(context, {
        actorId: request.user.id,
        actorNickname: request.user.nickname,
        type: input.type === 'instant' ? 'letter_instant' : 'letter_capsule',
        entityType: 'letter',
        entityId: row.id,
        title: input.type === 'instant'
          ? '你收到了一封情书'
          : `${request.user.nickname} 创建了一个时间胶囊`,
        payload: { letterType: input.type, letterTitle: input.title },
        link: linked,
      });
    }
    return reply.code(201).send(serializeLetter({
      ...row,
      myOpenedAt: new Date(),
      recipientOpenedAt: null,
    }, request.user.id));
  });

  app.put('/api/letters/:id', { preHandler: auth }, async (request) => {
    const spaceIds = await readableSpaceIds(context, request.user.id);
    const linked = await getActiveCoupleLink(context, request.user.id);
    const { id } = idSchema.parse(request.params);
    const input = letterInput.parse(request.body);
    if (input.type === 'instant' && !linked) {
      throw badRequest('INSTANT_REQUIRES_PARTNER', '绑定另一半后才能发送即时信');
    }
    const unlockOnPartnerBind = input.type === 'capsule' && Boolean(input.unlockOnPartnerBind);
    const unlockAt = input.type === 'capsule' && !unlockOnPartnerBind ? input.unlockAt ?? null : null;
    const result = await db.query<LetterRow>(
      `update letters set title = $4, content = $5, type = $6, unlock_at = $7,
         unlock_on_partner_bind = $8, updated_at = now()
       where id = $1 and space_id = any($2::uuid[]) and sender_id = $3
       returning id, sender_id as "senderId", $9::text as "senderNickname",
                 title, content, type, unlock_at as "unlockAt",
                 unlock_on_partner_bind as "unlockOnPartnerBind", created_at as "createdAt"`,
      [
        id,
        spaceIds,
        request.user.id,
        input.title,
        input.content,
        input.type,
        unlockAt,
        unlockOnPartnerBind,
        request.user.nickname,
      ],
    );
    if (!result.rowCount) throw forbidden('仅作者可编辑信件');
    const refreshed = await findLetter(context, spaceIds, id, request.user.id);
    return serializeLetter(refreshed!, request.user.id);
  });

  app.delete('/api/letters/:id', { preHandler: auth }, async (request) => {
    const spaceIds = await readableSpaceIds(context, request.user.id);
    const { id } = idSchema.parse(request.params);
    const result = await db.query(
      `delete from letters where id = $1 and space_id = any($2::uuid[]) and sender_id = $3`,
      [id, spaceIds, request.user.id],
    );
    if (!result.rowCount) throw forbidden('信件不存在或仅作者可删除');
    return { ok: true };
  });
}

function selectLetters(viewerParamIndex = 1) {
  // $1 = viewerId when used with find/list patterns below
  void viewerParamIndex;
  return `
  select l.id, l.sender_id as "senderId", u.nickname as "senderNickname",
         l.title, l.content, l.type, l.unlock_at as "unlockAt",
         l.unlock_on_partner_bind as "unlockOnPartnerBind", l.created_at as "createdAt",
         my_read.opened_at as "myOpenedAt",
         recipient_read.opened_at as "recipientOpenedAt"
  from letters l
  join users u on u.id = l.sender_id
  left join letter_item_reads my_read
    on my_read.letter_id = l.id and my_read.user_id = $1
  left join letter_item_reads recipient_read
    on recipient_read.letter_id = l.id and recipient_read.user_id <> l.sender_id`;
}

async function findLetter(
  context: AppContext,
  spaceIds: string[],
  id: string,
  viewerId: string,
) {
  const result = await context.db.query<LetterRow>(
    `${selectLetters()} where l.id = $2 and l.space_id = any($3::uuid[])`,
    [viewerId, id, spaceIds],
  );
  return result.rows[0] ?? null;
}

/** 未读 = 对方发来、已解锁、本人尚未拆开（与列表「未拆」一致） */
async function countUnreadLetters(
  context: AppContext,
  userId: string,
  spaceIds: string[],
) {
  const result = await context.db.query<{ count: string }>(
    `select count(*)::text as count
     from letters l
     where l.space_id = any($2::uuid[])
       and l.sender_id <> $1
       and (
         l.type = 'instant'
         or (l.unlock_at is not null and l.unlock_at <= now())
       )
       and not exists (
         select 1 from letter_item_reads r
         where r.user_id = $1 and r.letter_id = l.id
       )`,
    [userId, spaceIds],
  );
  return Number(result.rows[0]?.count ?? 0);
}
