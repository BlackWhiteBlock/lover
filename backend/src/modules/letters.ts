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
  base.extend({ type: z.literal('instant'), unlockAt: z.null().optional() }).strict(),
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
}

export function serializeLetter(row: LetterRow, now = new Date()) {
  const unlockOnPartnerBind = Boolean(row.unlockOnPartnerBind);
  const unlocked = isLetterUnlocked(row.type, row.unlockAt, now, unlockOnPartnerBind);
  return {
    id: row.id,
    senderId: row.senderId,
    senderNickname: row.senderNickname,
    title: row.title,
    type: row.type,
    unlockAt: row.unlockAt,
    unlockOnPartnerBind,
    isUnlocked: unlocked,
    createdAt: row.createdAt,
    ...(unlocked ? { content: row.content, summary: row.content.slice(0, 120) } : {}),
  };
}

export function registerLetters(app: FastifyInstance, context: AppContext, auth: AuthHandler) {
  const { db } = context;

  app.get('/api/letters', { preHandler: auth }, async (request) => {
    const spaceIds = await readableSpaceIds(context, request.user.id);
    const result = await db.query<LetterRow>(
      `${selectLetters} where l.space_id = any($1::uuid[]) order by l.created_at desc`,
      [spaceIds],
    );
    return { items: result.rows.map((row) => serializeLetter(row)) };
  });

  app.get('/api/letters/:id', { preHandler: auth }, async (request) => {
    const spaceIds = await readableSpaceIds(context, request.user.id);
    const { id } = idSchema.parse(request.params);
    const row = await findLetter(context, spaceIds, id);
    if (!row) throw notFound('信件不存在');
    return serializeLetter(row);
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
    return reply.code(201).send(serializeLetter(row));
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
    return serializeLetter(result.rows[0]!);
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

const selectLetters = `
  select l.id, l.sender_id as "senderId", u.nickname as "senderNickname",
         l.title, l.content, l.type, l.unlock_at as "unlockAt",
         l.unlock_on_partner_bind as "unlockOnPartnerBind", l.created_at as "createdAt"
  from letters l join users u on u.id = l.sender_id`;

async function findLetter(context: AppContext, spaceIds: string[], id: string) {
  const result = await context.db.query<LetterRow>(
    `${selectLetters} where l.id = $1 and l.space_id = any($2::uuid[])`,
    [id, spaceIds],
  );
  return result.rows[0] ?? null;
}
