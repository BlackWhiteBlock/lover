import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import type { AppContext, AuthHandler } from '../types.js';
import { isLetterUnlocked } from '../domain.js';
import { forbidden, notFound } from '../errors.js';
import { activeSpaceId } from './couples.js';

const base = z.object({
  title: z.string().trim().min(1).max(80),
  content: z.string().trim().min(1).max(20_000),
});
const letterInput = z.discriminatedUnion('type', [
  base.extend({ type: z.literal('instant'), unlockAt: z.null().optional() }).strict(),
  base.extend({
    type: z.literal('capsule'),
    unlockAt: z.string().datetime({ offset: true }).refine((value) => new Date(value) > new Date(), '解锁时间必须晚于当前时间'),
  }).strict(),
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
  createdAt: Date;
}

export function serializeLetter(row: LetterRow, now = new Date()) {
  const unlocked = isLetterUnlocked(row.type, row.unlockAt, now);
  return {
    id: row.id,
    senderId: row.senderId,
    senderNickname: row.senderNickname,
    title: row.title,
    type: row.type,
    unlockAt: row.unlockAt,
    isUnlocked: unlocked,
    createdAt: row.createdAt,
    ...(unlocked ? { content: row.content, summary: row.content.slice(0, 120) } : {}),
  };
}

export function registerLetters(app: FastifyInstance, context: AppContext, auth: AuthHandler) {
  const { db } = context;

  app.get('/api/letters', { preHandler: auth }, async (request) => {
    const spaceId = await activeSpaceId(context, request.user.id);
    const result = await db.query<LetterRow>(
      `${selectLetters} where l.space_id = $1 order by l.created_at desc`,
      [spaceId],
    );
    return { items: result.rows.map((row) => serializeLetter(row)) };
  });

  app.get('/api/letters/:id', { preHandler: auth }, async (request) => {
    const spaceId = await activeSpaceId(context, request.user.id);
    const { id } = idSchema.parse(request.params);
    const row = await findLetter(context, spaceId, id);
    if (!row) throw notFound('信件不存在');
    return serializeLetter(row);
  });

  app.post('/api/letters', { preHandler: auth }, async (request, reply) => {
    const spaceId = await activeSpaceId(context, request.user.id);
    const input = letterInput.parse(request.body);
    const result = await db.query<LetterRow>(
      `insert into letters(space_id, sender_id, title, content, type, unlock_at)
       values ($1, $2, $3, $4, $5, $6)
       returning id, sender_id as "senderId", $7::text as "senderNickname",
                 title, content, type, unlock_at as "unlockAt", created_at as "createdAt"`,
      [spaceId, request.user.id, input.title, input.content, input.type, input.type === 'capsule' ? input.unlockAt : null, request.user.nickname],
    );
    return reply.code(201).send(serializeLetter(result.rows[0]!));
  });

  app.put('/api/letters/:id', { preHandler: auth }, async (request) => {
    const spaceId = await activeSpaceId(context, request.user.id);
    const { id } = idSchema.parse(request.params);
    const input = letterInput.parse(request.body);
    const result = await db.query<LetterRow>(
      `update letters set title = $4, content = $5, type = $6, unlock_at = $7, updated_at = now()
       where id = $1 and space_id = $2 and sender_id = $3
       returning id, sender_id as "senderId", $8::text as "senderNickname",
                 title, content, type, unlock_at as "unlockAt", created_at as "createdAt"`,
      [id, spaceId, request.user.id, input.title, input.content, input.type, input.type === 'capsule' ? input.unlockAt : null, request.user.nickname],
    );
    if (!result.rowCount) throw forbidden('仅作者可编辑信件');
    return serializeLetter(result.rows[0]!);
  });

  app.delete('/api/letters/:id', { preHandler: auth }, async (request) => {
    const spaceId = await activeSpaceId(context, request.user.id);
    const { id } = idSchema.parse(request.params);
    const result = await db.query(
      `delete from letters where id = $1 and space_id = $2 and sender_id = $3`,
      [id, spaceId, request.user.id],
    );
    if (!result.rowCount) throw forbidden('信件不存在或仅作者可删除');
    return { ok: true };
  });
}

const selectLetters = `
  select l.id, l.sender_id as "senderId", u.nickname as "senderNickname",
         l.title, l.content, l.type, l.unlock_at as "unlockAt", l.created_at as "createdAt"
  from letters l join users u on u.id = l.sender_id`;

async function findLetter(context: AppContext, spaceId: string, id: string) {
  const result = await context.db.query<LetterRow>(`${selectLetters} where l.id = $1 and l.space_id = $2`, [id, spaceId]);
  return result.rows[0] ?? null;
}
