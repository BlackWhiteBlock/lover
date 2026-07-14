import { createHash, randomBytes } from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import type { AppContext, AuthHandler } from '../types.js';
import { badRequest, conflict, forbidden, notFound } from '../errors.js';
import { canAcceptInvite } from '../domain.js';

const dateSchema = z.string().regex(/^\d{4}-\d{2}-\d{2}$/).refine((value) => {
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.valueOf()) && date.toISOString().slice(0, 10) === value;
}, '日期无效');
const createInviteSchema = z.object({
  togetherDate: dateSchema.optional(),
  spaceName: z.string().trim().min(1).max(40).optional(),
}).strict();
const acceptSchema = z.object({ code: z.string().trim().min(6).max(32) }).strict();
const updateSchema = z.object({
  name: z.string().trim().min(1).max(40).optional(),
  togetherDate: dateSchema.nullable().optional(),
}).strict().refine((value) => Object.keys(value).length > 0, '至少提供一个更新字段');
const unbindSchema = z.object({ reason: z.string().trim().max(300).optional() }).strict();

const hashCode = (code: string) => createHash('sha256').update(code.toUpperCase()).digest('hex');

export async function activeSpaceId(context: AppContext, userId: string) {
  const result = await context.db.query<{ space_id: string }>(
    `select space_id from couple_members where user_id = $1 and status = 'active'`,
    [userId],
  );
  if (!result.rows[0]) throw forbidden('请先绑定情侣空间');
  return result.rows[0].space_id;
}

export function registerCouples(app: FastifyInstance, context: AppContext, auth: AuthHandler) {
  const { db } = context;

  app.get('/api/couple-space', { preHandler: auth }, async (request) => {
    const spaceId = await activeSpaceId(context, request.user.id);
    const [space, members, unbinding] = await Promise.all([
      db.query(
        `select id, name, together_date as "togetherDate", status, created_at as "createdAt"
         from couple_spaces where id = $1`,
        [spaceId],
      ),
      db.query(
        `select u.id, u.nickname, u.avatar_url as "avatarUrl", m.joined_at as "joinedAt"
         from couple_members m join users u on u.id = m.user_id
         where m.space_id = $1 and m.status = 'active' order by m.joined_at`,
        [spaceId],
      ),
      db.query(
        `select id, requested_by as "requestedBy", status, reason, created_at as "createdAt"
         from unbinding_requests where space_id = $1 and status = 'pending'`,
        [spaceId],
      ),
    ]);
    return { ...space.rows[0], members: members.rows, pendingUnbinding: unbinding.rows[0] ?? null };
  });

  app.patch('/api/couple-space', { preHandler: auth }, async (request) => {
    const spaceId = await activeSpaceId(context, request.user.id);
    const input = updateSchema.parse(request.body);
    const result = await db.query(
      `update couple_spaces set
         name = case when $2::boolean then $3 else name end,
         together_date = case when $4::boolean then $5::date else together_date end,
         updated_at = now()
       where id = $1 and status = 'active'
       returning id, name, together_date as "togetherDate", status`,
      [spaceId, input.name !== undefined, input.name ?? null, input.togetherDate !== undefined, input.togetherDate ?? null],
    );
    return result.rows[0];
  });

  app.post('/api/couple-invites', {
    preHandler: auth,
    config: { rateLimit: { max: 10, timeWindow: '1 hour' } },
  }, async (request, reply) => {
    const input = createInviteSchema.parse(request.body);
    const code = randomBytes(5).toString('hex').toUpperCase();
    const invite = await db.transaction(async (client) => {
      await client.query(`select pg_advisory_xact_lock(hashtext($1))`, [request.user.id]);
      let membership = await client.query<{ space_id: string }>(
        `select space_id from couple_members where user_id = $1 and status = 'active' for update`,
        [request.user.id],
      );
      let spaceId = membership.rows[0]?.space_id;
      if (!spaceId) {
        const created = await client.query<{ id: string }>(
          `insert into couple_spaces(name, together_date) values ($1, $2) returning id`,
          [input.spaceName ?? '我们的小宇宙', input.togetherDate ?? null],
        );
        spaceId = created.rows[0]!.id;
        await client.query(
          `insert into couple_members(space_id, user_id, status) values ($1, $2, 'active')`,
          [spaceId, request.user.id],
        );
      }
      const count = await client.query<{ count: number }>(
        `select count(*)::int as count from couple_members where space_id = $1 and status = 'active'`,
        [spaceId],
      );
      if ((count.rows[0]?.count ?? 0) >= 2) throw conflict('SPACE_FULL', '情侣空间已有两名成员');
      await client.query(
        `update couple_invites set status = 'cancelled'
         where space_id = $1 and inviter_id = $2 and status = 'pending'`,
        [spaceId, request.user.id],
      );
      const result = await client.query(
        `insert into couple_invites(space_id, inviter_id, code_hash, expires_at)
         values ($1, $2, $3, now() + interval '7 days')
         returning id, space_id as "spaceId", expires_at as "expiresAt"`,
        [spaceId, request.user.id, hashCode(code)],
      );
      return result.rows[0];
    });
    return reply.code(201).send({ ...invite, code });
  });

  app.post('/api/couple-invites/accept', {
    preHandler: auth,
    config: { rateLimit: { max: 20, timeWindow: '1 hour' } },
  }, async (request) => {
    const { code } = acceptSchema.parse(request.body);
    return db.transaction(async (client) => {
      const inviteResult = await client.query<{
        id: string; space_id: string; inviter_id: string; status: string; expires_at: Date;
      }>(
        `select id, space_id, inviter_id, status, expires_at
         from couple_invites where code_hash = $1 for update`,
        [hashCode(code)],
      );
      const invite = inviteResult.rows[0];
      if (!invite) throw notFound('邀请码不存在');
      if (invite.inviter_id === request.user.id) throw badRequest('SELF_INVITE', '不能接受自己的邀请');
      await client.query(`select pg_advisory_xact_lock(hashtext($1))`, [request.user.id]);
      const [count, current] = await Promise.all([
        client.query<{ count: number }>(
          `select count(*)::int as count from couple_members where space_id = $1 and status = 'active'`,
          [invite.space_id],
        ),
        client.query<{ space_id: string }>(
          `select space_id from couple_members where user_id = $1 and status = 'active'`,
          [request.user.id],
        ),
      ]);
      const decision = canAcceptInvite({
        inviteStatus: invite.status,
        expiresAt: invite.expires_at,
        memberCount: count.rows[0]?.count ?? 0,
        inviteeActiveSpaceId: current.rows[0]?.space_id ?? null,
        inviteSpaceId: invite.space_id,
      });
      if (!decision.ok) throw conflict('INVITE_NOT_ACCEPTABLE', decision.reason);
      await client.query(
        `insert into couple_members(space_id, user_id, status)
         values ($1, $2, 'active')`,
        [invite.space_id, request.user.id],
      );
      await client.query(`update couple_invites set status = 'accepted', accepted_by = $2 where id = $1`, [
        invite.id,
        request.user.id,
      ]);
      return { spaceId: invite.space_id };
    });
  });

  app.delete('/api/couple-invites/:id', { preHandler: auth }, async (request) => {
    const { id } = z.object({ id: z.string().uuid() }).strict().parse(request.params);
    const result = await db.query(
      `update couple_invites set status = 'cancelled'
       where id = $1 and inviter_id = $2 and status = 'pending' returning id`,
      [id, request.user.id],
    );
    if (!result.rowCount) throw notFound('邀请不存在或不可取消');
    return { ok: true };
  });

  app.post('/api/couple-space/unbinding', { preHandler: auth }, async (request, reply) => {
    const spaceId = await activeSpaceId(context, request.user.id);
    const { reason } = unbindSchema.parse(request.body);
    const members = await db.query<{ count: number }>(
      `select count(*)::int as count from couple_members where space_id = $1 and status = 'active'`,
      [spaceId],
    );
    if ((members.rows[0]?.count ?? 0) < 2) throw badRequest('NO_PARTNER', '当前空间没有可确认解绑的伴侣');
    try {
      const result = await db.query(
        `insert into unbinding_requests(space_id, requested_by, reason)
         values ($1, $2, $3)
         returning id, status, reason, created_at as "createdAt"`,
        [spaceId, request.user.id, reason ?? null],
      );
      return reply.code(201).send(result.rows[0]);
    } catch (error: unknown) {
      if ((error as { code?: string }).code === '23505') throw conflict('UNBINDING_PENDING', '已有待处理的解绑申请');
      throw error;
    }
  });

  app.post('/api/couple-space/unbinding/:id/confirm', { preHandler: auth }, async (request) => {
    const spaceId = await activeSpaceId(context, request.user.id);
    const { id } = z.object({ id: z.string().uuid() }).strict().parse(request.params);
    return db.transaction(async (client) => {
      const pending = await client.query<{ requested_by: string }>(
        `select requested_by from unbinding_requests
         where id = $1 and space_id = $2 and status = 'pending' for update`,
        [id, spaceId],
      );
      const row = pending.rows[0];
      if (!row) throw notFound('解绑申请不存在');
      if (row.requested_by === request.user.id) throw forbidden('申请人不能自行确认解绑');
      await client.query(
        `update unbinding_requests set status = 'confirmed', confirmed_by = $2, resolved_at = now() where id = $1`,
        [id, request.user.id],
      );
      await client.query(
        `update couple_members set status = 'inactive', left_at = now()
         where space_id = $1 and status = 'active'`,
        [spaceId],
      );
      await client.query(`update couple_spaces set status = 'dissolved', dissolved_at = now() where id = $1`, [spaceId]);
      await client.query(`update couple_invites set status = 'cancelled' where space_id = $1 and status = 'pending'`, [spaceId]);
      return { ok: true };
    });
  });

  app.post('/api/couple-space/unbinding/:id/cancel', { preHandler: auth }, async (request) => {
    const spaceId = await activeSpaceId(context, request.user.id);
    const { id } = z.object({ id: z.string().uuid() }).strict().parse(request.params);
    const result = await db.query(
      `update unbinding_requests set status = 'cancelled', resolved_at = now()
       where id = $1 and space_id = $2 and status = 'pending' returning id`,
      [id, spaceId],
    );
    if (!result.rowCount) throw notFound('解绑申请不存在');
    return { ok: true };
  });
}
