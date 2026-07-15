import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import type { AppContext, AuthHandler } from '../types.js';
import { badRequest, conflict, forbidden, notFound } from '../errors.js';
import { getActiveCoupleLink, personalSpaceId } from './spaces.js';
import { presentUserAvatar } from './userAvatar.js';

export { activeSpaceId, writeSpaceId, personalSpaceId, readableSpaceIds } from './spaces.js';

const dateSchema = z.string().regex(/^\d{4}-\d{2}-\d{2}$/).refine((value) => {
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.valueOf()) && date.toISOString().slice(0, 10) === value;
}, '日期无效');

const onboardingSchema = z.object({
  nickname: z.string().trim().min(1).max(30),
  avatarUrl: z.string().url().max(500).nullable().optional(),
  gender: z.enum(['male', 'female', 'unspecified']),
  birthday: dateSchema,
  spaceName: z.string().trim().min(1).max(40),
}).strict();

const bindPhoneSchema = z.object({
  phone: z.string().trim()
    .transform((value) => value.replace(/[\s-]/g, '').replace(/^\+?86/, ''))
    .refine((phone) => /^1[3-9]\d{9}$/.test(phone), '请输入有效手机号'),
}).strict();

const updateLinkSchema = z.object({
  togetherDate: dateSchema.nullable().optional(),
  name: z.string().trim().min(1).max(40).optional(),
}).strict().refine((value) => Object.keys(value).length > 0, '至少提供一个更新字段');

const updateSpaceSchema = z.object({
  name: z.string().trim().min(1).max(40),
}).strict();

const idSchema = z.object({ id: z.string().uuid() }).strict();
const unbindSchema = z.object({ reason: z.string().trim().max(300).optional() }).strict();

type UserCard = {
  id: string;
  phone: string;
  nickname: string;
  avatarUrl: string | null;
  gender: string | null;
  birthday: string | null;
};

export function registerCouples(app: FastifyInstance, context: AppContext, auth: AuthHandler) {
  const { db } = context;

  app.post('/api/onboarding', { preHandler: auth }, async (request, reply) => {
    const input = onboardingSchema.parse(request.body);
    const result = await db.transaction(async (client) => {
      await client.query(`select pg_advisory_xact_lock(hashtext($1))`, [request.user.id]);
      const me = await client.query<{ profile_completed: boolean; personal_space_id: string | null }>(
        `select profile_completed, personal_space_id from users where id = $1 for update`,
        [request.user.id],
      );
      const row = me.rows[0];
      if (!row) throw notFound('用户不存在');
      if (row.profile_completed && row.personal_space_id) {
        throw conflict('ALREADY_ONBOARDED', '已完成空间创建');
      }

      const space = await client.query<{ id: string; name: string }>(
        `insert into couple_spaces(name, together_date, kind)
         values ($1, null, 'personal')
         returning id, name`,
        [input.spaceName],
      );
      const spaceId = space.rows[0]!.id;
      await client.query(
        `insert into couple_members(space_id, user_id, status) values ($1, $2, 'active')`,
        [spaceId, request.user.id],
      );
      const user = await client.query<UserCard>(
        `update users set
           nickname = $2,
           avatar_url = coalesce($3, avatar_url),
           gender = $4,
           birthday = $5::date,
           profile_completed = true,
           personal_space_id = $6,
           updated_at = now()
         where id = $1
         returning id, phone, nickname, avatar_url as "avatarUrl", gender, birthday::text as birthday`,
        [
          request.user.id,
          input.nickname,
          input.avatarUrl ?? null,
          input.gender,
          input.birthday,
          spaceId,
        ],
      );
      return { user: user.rows[0]!, personalSpaceId: spaceId, spaceName: space.rows[0]!.name };
    });
    return reply.code(201).send(result);
  });

  app.get('/api/couple-space', { preHandler: auth }, async (request) => {
    await db.query(
      `update couple_bind_requests
       set status = 'expired', resolved_at = coalesce(resolved_at, now())
       where status = 'pending' and expires_at <= now()
         and (requester_id = $1 or target_user_id = $1)`,
      [request.user.id],
    );
    const personalId = await personalSpaceId(context, request.user.id);
    const link = await getActiveCoupleLink(context, request.user.id);
    const space = await db.query(
      `select id, name, kind, together_date::text as "togetherDate", status,
              created_at::text as "createdAt"
       from couple_spaces where id = $1`,
      [link?.loverSpaceId ?? personalId],
    );
    const members = link
      ? await db.query<{
        id: string; nickname: string; avatarUrl: string | null; avatarAssetId: string | null;
        gender: string | null; birthday: string | null;
      }>(
        `select u.id, u.nickname, u.avatar_url as "avatarUrl",
                u.avatar_asset_id as "avatarAssetId",
                u.gender, u.birthday::text as birthday
         from users u where u.id = any($1::uuid[])`,
        [[link.userAId, link.userBId]],
      )
      : await db.query<{
        id: string; nickname: string; avatarUrl: string | null; avatarAssetId: string | null;
        gender: string | null; birthday: string | null;
      }>(
        `select u.id, u.nickname, u.avatar_url as "avatarUrl",
                u.avatar_asset_id as "avatarAssetId",
                u.gender, u.birthday::text as birthday
         from users u where u.id = $1`,
        [request.user.id],
      );
    const memberCards = await Promise.all(
      members.rows.map(async (member) => {
        const avatarUrl = await presentUserAvatar(context, member, request.user.id);
        const { avatarAssetId: _assetId, ...rest } = member;
        return { ...rest, avatarUrl };
      }),
    );

    const pendingIncoming = await db.query<{
      id: string; requesterId: string; status: string; expiresAt: string; createdAt: string;
      requesterNickname: string; requesterPhone: string;
      requesterAvatarUrl: string | null; requesterAvatarAssetId: string | null;
    }>(
      `select r.id, r.requester_id as "requesterId", r.status,
              r.expires_at::text as "expiresAt",
              r.created_at::text as "createdAt",
              u.nickname as "requesterNickname", u.phone as "requesterPhone",
              u.avatar_url as "requesterAvatarUrl",
              u.avatar_asset_id as "requesterAvatarAssetId"
       from couple_bind_requests r
       join users u on u.id = r.requester_id
       where r.target_user_id = $1 and r.status = 'pending' and r.expires_at > now()
       order by r.created_at desc`,
      [request.user.id],
    );
    const pendingOutgoing = await db.query<{
      id: string; targetUserId: string; status: string; expiresAt: string; createdAt: string;
      targetNickname: string; targetPhone: string;
      targetAvatarUrl: string | null; targetAvatarAssetId: string | null;
    }>(
      `select r.id, r.target_user_id as "targetUserId", r.status,
              r.expires_at::text as "expiresAt",
              r.created_at::text as "createdAt",
              u.nickname as "targetNickname", u.phone as "targetPhone",
              u.avatar_url as "targetAvatarUrl",
              u.avatar_asset_id as "targetAvatarAssetId"
       from couple_bind_requests r
       join users u on u.id = r.target_user_id
       where r.requester_id = $1 and r.status = 'pending' and r.expires_at > now()
       order by r.created_at desc
       limit 1`,
      [request.user.id],
    );
    const incomingCards = await Promise.all(
      pendingIncoming.rows.map(async (row) => {
        const requesterAvatarUrl = await presentUserAvatar(
          context,
          { avatarUrl: row.requesterAvatarUrl, avatarAssetId: row.requesterAvatarAssetId },
          request.user.id,
        );
        const { requesterAvatarAssetId: _a, ...rest } = row;
        return { ...rest, requesterAvatarUrl };
      }),
    );
    const outgoingCards = await Promise.all(
      pendingOutgoing.rows.map(async (row) => {
        const targetAvatarUrl = await presentUserAvatar(
          context,
          { avatarUrl: row.targetAvatarUrl, avatarAssetId: row.targetAvatarAssetId },
          request.user.id,
        );
        const { targetAvatarAssetId: _a, ...rest } = row;
        return { ...rest, targetAvatarUrl };
      }),
    );

    let pendingUnbinding = null;
    if (link) {
      const unbinding = await db.query(
        `select id, requested_by as "requestedBy", status, reason, created_at as "createdAt"
         from unbinding_requests where space_id = $1 and status = 'pending'`,
        [link.loverSpaceId],
      );
      pendingUnbinding = unbinding.rows[0] ?? null;
    }

    return {
      ...space.rows[0],
      togetherDate: link?.togetherDate ?? null,
      linked: Boolean(link),
      coupleLinkId: link?.id ?? null,
      personalSpaceId: personalId,
      loverSpaceId: link?.loverSpaceId ?? null,
      members: memberCards,
      pendingUnbinding,
      pendingIncomingBinds: incomingCards,
      pendingOutgoingBind: outgoingCards[0] ?? null,
    };
  });

  app.patch('/api/couple-space', { preHandler: auth }, async (request) => {
    const personalId = await personalSpaceId(context, request.user.id);
    const input = updateSpaceSchema.parse(request.body);
    const result = await db.query(
      `update couple_spaces set name = $2, updated_at = now()
       where id = $1 and kind = 'personal' and status = 'active'
       returning id, name, kind, status`,
      [personalId, input.name],
    );
    if (!result.rowCount) throw notFound('个人空间不存在');
    return result.rows[0];
  });

  app.patch('/api/couple-link', { preHandler: auth }, async (request) => {
    const link = await getActiveCoupleLink(context, request.user.id);
    if (!link) throw forbidden('请先完成伴侣绑定');
    const input = updateLinkSchema.parse(request.body);
    if (input.name !== undefined) {
      await db.query(
        `update couple_spaces set name = $2, updated_at = now() where id = $1 and kind = 'lover'`,
        [link.loverSpaceId, input.name],
      );
    }
    if (input.togetherDate !== undefined) {
      await db.query(
        `update couple_links set together_date = $2 where id = $1 and status = 'active'`,
        [link.id, input.togetherDate],
      );
    }
    const refreshed = await getActiveCoupleLink(context, request.user.id);
    return {
      id: refreshed!.id,
      loverSpaceId: refreshed!.loverSpaceId,
      togetherDate: refreshed!.togetherDate,
    };
  });

  app.post('/api/couple-binds', {
    preHandler: auth,
    config: { rateLimit: { max: 20, timeWindow: '1 hour' } },
  }, async (request, reply) => {
    const { phone } = bindPhoneSchema.parse(request.body);
    if (phone === request.user.phone) throw badRequest('SELF_BIND', '不能绑定自己的手机号');
    await personalSpaceId(context, request.user.id);
    if (await getActiveCoupleLink(context, request.user.id)) {
      throw conflict('ALREADY_LINKED', '你已绑定另一半');
    }

    const target = await db.query<{ id: string; profile_completed: boolean }>(
      `select id, profile_completed from users where phone = $1`,
      [phone],
    );
    if (!target.rowCount) throw notFound('该手机号尚未注册 Lover');
    const targetUser = target.rows[0]!;
    if (!targetUser.profile_completed) throw badRequest('TARGET_INCOMPLETE', '对方尚未完成空间创建');
    if (await getActiveCoupleLink(context, targetUser.id)) {
      throw conflict('TARGET_LINKED', '对方已绑定另一半');
    }

    // 过期待处理邀请落库标记，避免占用「仅一条待处理」唯一约束
    await db.query(
      `update couple_bind_requests
       set status = 'expired', resolved_at = coalesce(resolved_at, now())
       where status = 'pending' and expires_at <= now()
         and (requester_id = $1 or target_user_id = $1 or requester_id = $2 or target_user_id = $2)`,
      [request.user.id, targetUser.id],
    );

    const existingToMe = await db.query(
      `select id from couple_bind_requests
       where requester_id = $1 and target_user_id = $2 and status = 'pending' and expires_at > now()`,
      [targetUser.id, request.user.id],
    );
    if (existingToMe.rowCount) {
      throw conflict('REVERSE_PENDING', '对方已向你发起绑定，请先处理收到的请求');
    }

    const targetBusy = await db.query(
      `select id from couple_bind_requests
       where target_user_id = $1 and status = 'pending' and expires_at > now()
       limit 1`,
      [targetUser.id],
    );
    if (targetBusy.rowCount) {
      throw conflict('TARGET_BIND_PENDING', '对方已有待处理的绑定邀请，请稍后再试');
    }

    try {
      await db.query(
        `update couple_bind_requests set status = 'cancelled', resolved_at = now()
         where requester_id = $1 and status = 'pending'`,
        [request.user.id],
      );
      const created = await db.query(
        `insert into couple_bind_requests(requester_id, target_user_id, expires_at)
         values ($1, $2, now() + interval '24 hours')
         returning id, requester_id as "requesterId", target_user_id as "targetUserId",
                   status, expires_at as "expiresAt", created_at as "createdAt"`,
        [request.user.id, targetUser.id],
      );
      return reply.code(201).send(created.rows[0]);
    } catch (error: unknown) {
      if ((error as { code?: string }).code === '23505') {
        throw conflict('BIND_PENDING', '对方已有待处理的绑定邀请，或你已有待确认请求');
      }
      throw error;
    }
  });

  app.get('/api/couple-binds/pending', { preHandler: auth }, async (request) => {
    await db.query(
      `update couple_bind_requests
       set status = 'expired', resolved_at = coalesce(resolved_at, now())
       where status = 'pending' and expires_at <= now()
         and (requester_id = $1 or target_user_id = $1)`,
      [request.user.id],
    );
    const [incoming, outgoing] = await Promise.all([
      db.query(
        `select r.id, r.requester_id as "requesterId", r.status,
                r.expires_at::text as "expiresAt",
                r.created_at::text as "createdAt",
                u.nickname as "requesterNickname", u.phone as "requesterPhone",
                u.avatar_url as "requesterAvatarUrl"
         from couple_bind_requests r join users u on u.id = r.requester_id
         where r.target_user_id = $1 and r.status = 'pending' and r.expires_at > now()
         order by r.created_at desc`,
        [request.user.id],
      ),
      db.query(
        `select r.id, r.target_user_id as "targetUserId", r.status,
                r.expires_at::text as "expiresAt",
                r.created_at::text as "createdAt",
                u.nickname as "targetNickname", u.phone as "targetPhone",
                u.avatar_url as "targetAvatarUrl"
         from couple_bind_requests r join users u on u.id = r.target_user_id
         where r.requester_id = $1 and r.status = 'pending' and r.expires_at > now()
         order by r.created_at desc`,
        [request.user.id],
      ),
    ]);
    return { incoming: incoming.rows, outgoing: outgoing.rows };
  });

  app.post('/api/couple-binds/:id/accept', { preHandler: auth }, async (request) => {
    const { id } = idSchema.parse(request.params);
    return db.transaction(async (client) => {
      const req = await client.query<{
        id: string;
        requester_id: string;
        target_user_id: string;
        status: string;
        expires_at: Date;
      }>(
        `select id, requester_id, target_user_id, status, expires_at
         from couple_bind_requests where id = $1 for update`,
        [id],
      );
      const bind = req.rows[0];
      if (!bind) throw notFound('绑定请求不存在');
      if (bind.target_user_id !== request.user.id) throw forbidden('只能处理发给自己的绑定请求');
      if (bind.status !== 'pending') throw conflict('BIND_NOT_PENDING', '请求已处理');
      if (bind.expires_at <= new Date()) {
        await client.query(
          `update couple_bind_requests set status = 'expired', resolved_at = now() where id = $1`,
          [bind.id],
        );
        throw conflict('BIND_EXPIRED', '绑定请求已过期');
      }

      for (const uid of [bind.requester_id, bind.target_user_id]) {
        const linked = await client.query(
          `select id from couple_links where status = 'active' and (user_a_id = $1 or user_b_id = $1)`,
          [uid],
        );
        if (linked.rowCount) throw conflict('ALREADY_LINKED', '一方已完成绑定');
      }

      const [a, b] = bind.requester_id < bind.target_user_id
        ? [bind.requester_id, bind.target_user_id]
        : [bind.target_user_id, bind.requester_id];

      const lover = await client.query<{ id: string }>(
        `insert into couple_spaces(name, together_date, kind)
         values ('我们的小宇宙', null, 'lover') returning id`,
      );
      const loverSpaceId = lover.rows[0]!.id;
      await client.query(
        `insert into couple_members(space_id, user_id, status) values ($1, $2, 'active'), ($1, $3, 'active')`,
        [loverSpaceId, a, b],
      );
      const link = await client.query(
        `insert into couple_links(user_a_id, user_b_id, lover_space_id, status)
         values ($1, $2, $3, 'active')
         returning id, lover_space_id as "loverSpaceId", together_date as "togetherDate"`,
        [a, b, loverSpaceId],
      );
      await client.query(
        `update couple_bind_requests set status = 'accepted', resolved_at = now() where id = $1`,
        [bind.id],
      );
      await client.query(
        `update couple_bind_requests set status = 'cancelled', resolved_at = now()
         where status = 'pending' and id <> $1
           and (requester_id = any($2::uuid[]) or target_user_id = any($2::uuid[]))`,
        [bind.id, [a, b]],
      );

      // Auto-unlock capsules waiting for partner bind (both users' personal/couple letters).
      await client.query(
        `update letters
         set unlock_at = now(), unlock_on_partner_bind = false, updated_at = now()
         where unlock_on_partner_bind = true
           and type = 'capsule'
           and sender_id = any($1::uuid[])
           and (unlock_at is null or unlock_at > now())`,
        [[a, b]],
      );

      return {
        coupleLinkId: link.rows[0]!.id,
        loverSpaceId,
        togetherDate: null,
        needsTogetherDate: true,
      };
    });
  });

  app.post('/api/couple-binds/:id/reject', { preHandler: auth }, async (request) => {
    const { id } = idSchema.parse(request.params);
    const result = await db.query(
      `update couple_bind_requests set status = 'rejected', resolved_at = now()
       where id = $1 and target_user_id = $2 and status = 'pending' returning id`,
      [id, request.user.id],
    );
    if (!result.rowCount) throw notFound('绑定请求不存在');
    return { ok: true };
  });

  app.post('/api/couple-binds/:id/cancel', { preHandler: auth }, async (request) => {
    const { id } = idSchema.parse(request.params);
    const result = await db.query(
      `update couple_bind_requests set status = 'cancelled', resolved_at = now()
       where id = $1 and requester_id = $2 and status = 'pending' returning id`,
      [id, request.user.id],
    );
    if (!result.rowCount) throw notFound('绑定请求不存在');
    return { ok: true };
  });

  // Unbinding remains structured against lover space; content policy deferred.
  app.post('/api/couple-space/unbinding', { preHandler: auth }, async (request, reply) => {
    const link = await getActiveCoupleLink(context, request.user.id);
    if (!link) throw badRequest('NO_PARTNER', '当前没有可解绑的伴侣关系');
    const { reason } = unbindSchema.parse(request.body);
    try {
      const result = await db.query(
        `insert into unbinding_requests(space_id, requested_by, reason)
         values ($1, $2, $3)
         returning id, status, reason, created_at as "createdAt"`,
        [link.loverSpaceId, request.user.id, reason ?? null],
      );
      return reply.code(201).send(result.rows[0]);
    } catch (error: unknown) {
      if ((error as { code?: string }).code === '23505') throw conflict('UNBINDING_PENDING', '已有待处理的解绑申请');
      throw error;
    }
  });

  app.post('/api/couple-space/unbinding/:id/confirm', { preHandler: auth }, async (request) => {
    const link = await getActiveCoupleLink(context, request.user.id);
    if (!link) throw forbidden('请先绑定情侣空间');
    const { id } = idSchema.parse(request.params);
    return db.transaction(async (client) => {
      const pending = await client.query<{ id: string; requested_by: string }>(
        `select id, requested_by from unbinding_requests
         where id = $1 and space_id = $2 and status = 'pending' for update`,
        [id, link.loverSpaceId],
      );
      const row = pending.rows[0];
      if (!row) throw notFound('解绑申请不存在');
      if (row.requested_by === request.user.id) throw badRequest('SELF_CONFIRM', '不能确认自己发起的解绑');
      await client.query(
        `update unbinding_requests set status = 'confirmed', confirmed_by = $2, resolved_at = now() where id = $1`,
        [id, request.user.id],
      );
      await client.query(
        `update couple_members set status = 'inactive', left_at = now()
         where space_id = $1 and status = 'active'`,
        [link.loverSpaceId],
      );
      await client.query(
        `update couple_spaces set status = 'dissolved', dissolved_at = now() where id = $1`,
        [link.loverSpaceId],
      );
      await client.query(
        `update couple_links set status = 'ended', ended_at = now() where id = $1`,
        [link.id],
      );
      // Lover-space content retained with ownership=couple for future policy.
      return { ok: true };
    });
  });

  app.post('/api/couple-space/unbinding/:id/cancel', { preHandler: auth }, async (request) => {
    const link = await getActiveCoupleLink(context, request.user.id);
    if (!link) throw forbidden('请先绑定情侣空间');
    const { id } = idSchema.parse(request.params);
    const result = await db.query(
      `update unbinding_requests set status = 'cancelled', resolved_at = now()
       where id = $1 and space_id = $2 and status = 'pending' returning id`,
      [id, link.loverSpaceId],
    );
    if (!result.rowCount) throw notFound('解绑申请不存在');
    return { ok: true };
  });
}
