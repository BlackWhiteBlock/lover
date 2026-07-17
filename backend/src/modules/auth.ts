import { createHash, randomInt, randomUUID } from 'node:crypto';
import { createRequire } from 'node:module';
import type { SendSmsRequest, SendSmsResponse } from '@alicloud/dysmsapi20170525';
import type { $OpenApiUtil } from '@alicloud/openapi-core';
import bcrypt from 'bcryptjs';
import type { FastifyInstance, FastifyRequest } from 'fastify';
import jwt, { type SignOptions } from 'jsonwebtoken';
import { z } from 'zod';
import type { AppContext, AuthHandler, AuthUser, AccessClaims } from '../types.js';
import { AppError, badRequest, forbidden, unauthorized } from '../errors.js';
import { personalSpaceId } from './spaces.js';
import { presentUserAvatar } from './userAvatar.js';

const require = createRequire(import.meta.url);
type SmsClient = {
  sendSms(request: SendSmsRequest): Promise<SendSmsResponse>;
};

const phoneSchema = z.string().trim().transform(normalizePhone).refine(
  (phone) => /^1[3-9]\d{9}$/.test(phone),
  '请输入有效的中国大陆手机号',
);
const sendSchema = z.object({ phone: phoneSchema }).strict();
const loginSchema = z.object({
  phone: phoneSchema,
  code: z.string().regex(/^\d{6}$/),
  nickname: z.string().trim().min(1).max(30).optional(),
}).strict();
const refreshSchema = z.object({ refreshToken: z.string().min(1) }).strict();
const patchMeSchema = z.object({
  avatarAssetId: z.string().uuid().optional(),
  coupleCoverAssetId: z.string().uuid().nullable().optional(),
  clearCoupleCover: z.boolean().optional(),
}).strict().refine(
  (value) => value.avatarAssetId !== undefined
    || value.coupleCoverAssetId !== undefined
    || value.clearCoupleCover === true,
  '至少提供一个更新字段',
);
const avatarMime = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/heic']);

type MeProfileRow = AuthUser & {
  avatarAssetId: string | null;
  coupleCoverAssetId: string | null;
};

async function presentMeUser(context: AppContext, profile: MeProfileRow, viewerUserId: string) {
  const { avatarAssetId: _avatarAssetId, coupleCoverAssetId: _coverAssetId, ...rest } = profile;
  const [avatarUrl, coupleCoverUrl] = await Promise.all([
    presentUserAvatar(context, profile, viewerUserId),
    presentUserAvatar(
      context,
      { avatarUrl: null, avatarAssetId: profile.coupleCoverAssetId },
      viewerUserId,
    ),
  ]);
  return { ...rest, avatarUrl, coupleCoverUrl };
}

async function assertPersonalImageAsset(
  context: AppContext,
  userId: string,
  assetId: string,
  label: string,
) {
  const spaceId = await personalSpaceId(context, userId);
  const asset = await context.db.query<{ id: string; mime_type: string }>(
    `select id, mime_type from media_assets
     where id = $1 and space_id = $2 and status = 'ready'`,
    [assetId, spaceId],
  );
  const row = asset.rows[0];
  if (!row) throw forbidden(`${label}资产不存在或无权使用`);
  if (!avatarMime.has(row.mime_type)) {
    throw badRequest('INVALID_AVATAR_TYPE', `${label}仅支持图片`);
  }
}

function normalizePhone(value: string) {
  return value.replace(/[\s-]/g, '').replace(/^\+?86/, '');
}

function tokenHash(token: string) {
  return createHash('sha256').update(token).digest('hex');
}

export function registerAuth(app: FastifyInstance, context: AppContext) {
  const { db, config } = context;

  app.post('/api/auth/sms/send', {
    config: {
      rateLimit: config.sms.provider === 'dev'
        ? false
        : { max: 5, timeWindow: '10 minutes' },
    },
  }, async (request) => {
    const { phone } = sendSchema.parse(request.body);
    // 开发/测试短信模式不限流、不冷却，避免联调反复取码被拦住
    if (config.sms.provider !== 'dev') {
      const recent = await db.query(
        `select 1 from sms_codes where phone = $1 and created_at > now() - make_interval(secs => $2) limit 1`,
        [phone, config.sms.cooldownSeconds],
      );
      if (recent.rowCount) throw new AppError(429, 'SMS_RATE_LIMITED', '验证码发送过于频繁');
    }
    const code = config.sms.provider === 'dev'
      ? config.sms.devCode
      : String(randomInt(100000, 1_000_000));
    await db.query(
      `insert into sms_codes(phone, code_hash, expires_at)
       values ($1, $2, now() + make_interval(secs => $3))`,
      [phone, await bcrypt.hash(code, 10), config.sms.codeTtlSeconds],
    );
    await sendSms(context, phone, code);
    return {
      ok: true,
      cooldownSeconds: config.sms.provider === 'dev' ? 0 : config.sms.cooldownSeconds,
      ...(config.sms.provider === 'dev' ? { devCode: code } : {}),
    };
  });

  app.post('/api/auth/sms/login', {
    config: { rateLimit: { max: 10, timeWindow: '10 minutes' } },
  }, async (request, reply) => {
    const input = loginSchema.parse(request.body);
    const matches = await db.query<{ id: string; code_hash: string }>(
      `select id, code_hash from sms_codes
       where phone = $1 and consumed_at is null and expires_at > now()
       order by created_at desc limit 5`,
      [input.phone],
    );
    let codeId: string | null = null;
    for (const row of matches.rows) {
      if (await bcrypt.compare(input.code, row.code_hash)) {
        codeId = row.id;
        break;
      }
    }
    if (!codeId) throw unauthorized('验证码错误或已过期');

    const result = await db.transaction(async (client) => {
      const consumed = await client.query(
        `update sms_codes set consumed_at = now()
         where id = $1 and consumed_at is null returning id`,
        [codeId],
      );
      if (!consumed.rowCount) throw unauthorized('验证码已使用');
      let userResult = await client.query<AuthUser>(
        `select id, phone, nickname, avatar_url as "avatarUrl",
                gender, birthday::text as birthday,
                profile_completed as "profileCompleted",
                personal_space_id as "personalSpaceId"
         from users where phone = $1`,
        [input.phone],
      );
      const isNewUser = !userResult.rowCount;
      if (isNewUser) {
        userResult = await client.query<AuthUser>(
          `insert into users(phone, nickname) values ($1, $2)
           returning id, phone, nickname, avatar_url as "avatarUrl",
                     gender, birthday::text as birthday,
                     profile_completed as "profileCompleted",
                     personal_space_id as "personalSpaceId"`,
          [input.phone, input.nickname ?? `用户${input.phone.slice(-4)}`],
        );
      }
      const user = userResult.rows[0]!;
      // 单端登录：新登录立即作废该用户其它未过期会话，旧设备下次请求会 401。
      await client.query(
        `update auth_sessions
         set revoked_at = now()
         where user_id = $1 and revoked_at is null`,
        [user.id],
      );
      const sessionId = randomUUID();
      const refreshToken = signRefresh(context, user.id, sessionId);
      await client.query(
        `insert into auth_sessions(id, user_id, refresh_token_hash, expires_at)
         values ($1, $2, $3, now() + make_interval(days => $4))`,
        [sessionId, user.id, tokenHash(refreshToken), config.jwt.refreshTtlDays],
      );
      return { user, isNewUser, ...tokens(context, user.id, sessionId, refreshToken) };
    });
    return reply.code(result.isNewUser ? 201 : 200).send(result);
  });

  app.post('/api/auth/refresh', async (request) => {
    const { refreshToken } = refreshSchema.parse(request.body);
    let claims: jwt.JwtPayload;
    try {
      claims = jwt.verify(refreshToken, config.jwt.refreshSecret) as jwt.JwtPayload;
    } catch {
      throw unauthorized('刷新令牌无效或已过期');
    }
    if (claims.type !== 'refresh' || typeof claims.sub !== 'string' || typeof claims.sid !== 'string') {
      throw unauthorized('刷新令牌无效');
    }
    const nextRefresh = signRefresh(context, claims.sub, claims.sid);
    const rotated = await db.query(
      `update auth_sessions set refresh_token_hash = $4, last_used_at = now()
       where id = $1 and user_id = $2 and refresh_token_hash = $3
         and revoked_at is null and expires_at > now()
       returning id`,
      [claims.sid, claims.sub, tokenHash(refreshToken), tokenHash(nextRefresh)],
    );
    if (!rotated.rowCount) throw unauthorized('会话已失效');
    return tokens(context, claims.sub, claims.sid, nextRefresh);
  });

  app.post('/api/auth/logout', { preHandler: createAuthHandler(context) }, async (request) => {
    await db.query(`update auth_sessions set revoked_at = now() where id = $1`, [request.sessionId]);
    return { ok: true };
  });

  app.get('/api/me', { preHandler: createAuthHandler(context) }, async (request) => {
    const user = await db.query<MeProfileRow>(
      `select id, phone, nickname, avatar_url as "avatarUrl",
              avatar_asset_id as "avatarAssetId",
              couple_cover_asset_id as "coupleCoverAssetId",
              gender, birthday::text as birthday,
              profile_completed as "profileCompleted",
              personal_space_id as "personalSpaceId"
       from users where id = $1`,
      [request.user.id],
    );
    const link = await db.query<{ lover_space_id: string }>(
      `select lover_space_id from couple_links
       where status = 'active' and (user_a_id = $1 or user_b_id = $1)`,
      [request.user.id],
    );
    const profile = user.rows[0]!;
    return {
      user: await presentMeUser(context, profile, request.user.id),
      personalSpaceId: profile.personalSpaceId ?? null,
      loverSpaceId: link.rows[0]?.lover_space_id ?? null,
      activeSpaceId: link.rows[0]?.lover_space_id ?? profile.personalSpaceId ?? null,
      profileCompleted: Boolean(profile.profileCompleted),
      linked: Boolean(link.rowCount),
    };
  });

  app.patch('/api/me', { preHandler: createAuthHandler(context) }, async (request) => {
    const input = patchMeSchema.parse(request.body);
    if (input.avatarAssetId !== undefined) {
      await assertPersonalImageAsset(context, request.user.id, input.avatarAssetId, '头像');
    }
    const clearCover = input.clearCoupleCover === true || input.coupleCoverAssetId === null;
    if (input.coupleCoverAssetId) {
      await assertPersonalImageAsset(context, request.user.id, input.coupleCoverAssetId, '情侣头像');
    }

    const updated = await db.query<MeProfileRow>(
      `update users set
         avatar_asset_id = coalesce($2::uuid, avatar_asset_id),
         couple_cover_asset_id = case
           when $4::boolean then null
           when $3::uuid is not null then $3::uuid
           else couple_cover_asset_id
         end,
         updated_at = now()
       where id = $1
       returning id, phone, nickname, avatar_url as "avatarUrl",
                 avatar_asset_id as "avatarAssetId",
                 couple_cover_asset_id as "coupleCoverAssetId",
                 gender, birthday::text as birthday,
                 profile_completed as "profileCompleted",
                 personal_space_id as "personalSpaceId"`,
      [
        request.user.id,
        input.avatarAssetId ?? null,
        clearCover ? null : (input.coupleCoverAssetId ?? null),
        clearCover,
      ],
    );
    const profile = updated.rows[0]!;
    return { user: await presentMeUser(context, profile, request.user.id) };
  });
}

export function createAuthHandler(context: AppContext): AuthHandler {
  return async (request) => {
    const header = request.headers.authorization;
    if (!header?.startsWith('Bearer ')) throw unauthorized();
    let claims: AccessClaims;
    try {
      claims = jwt.verify(header.slice(7), context.config.jwt.accessSecret) as AccessClaims;
    } catch {
      throw unauthorized('访问令牌无效或已过期');
    }
    if (claims.type !== 'access' || !claims.sub || !claims.sid) throw unauthorized();
    const result = await context.db.query<AuthUser>(
      `select u.id, u.phone, u.nickname, u.avatar_url as "avatarUrl",
              u.gender, u.birthday::text as birthday,
              u.profile_completed as "profileCompleted",
              u.personal_space_id as "personalSpaceId"
       from users u join auth_sessions s on s.user_id = u.id
       where u.id = $1 and s.id = $2 and s.revoked_at is null and s.expires_at > now()`,
      [claims.sub, claims.sid],
    );
    if (!result.rowCount) throw unauthorized('会话已失效');
    request.user = result.rows[0]!;
    request.sessionId = claims.sid;
  };
}

function tokens(context: AppContext, userId: string, sessionId: string, refreshToken: string) {
  const accessToken = jwt.sign(
    { sub: userId, sid: sessionId, type: 'access' },
    context.config.jwt.accessSecret,
    { expiresIn: context.config.jwt.accessTtl as SignOptions['expiresIn'] },
  );
  return {
    accessToken,
    refreshToken,
    tokenType: 'Bearer',
    expiresIn: context.config.jwt.accessTtl,
  };
}

function signRefresh(context: AppContext, userId: string, sessionId: string) {
  return jwt.sign(
    { sub: userId, sid: sessionId, type: 'refresh' },
    context.config.jwt.refreshSecret,
    { expiresIn: `${context.config.jwt.refreshTtlDays}d` },
  );
}

async function sendSms(context: AppContext, phone: string, code: string) {
  if (context.config.sms.provider === 'dev') {
    console.info(`[SMS dev] ${phone}: ${code}`);
    return;
  }
  // Loaded only in production SMS mode, keeping local development dependency-free from cloud credentials.
  const Dysmsapi = require('@alicloud/dysmsapi20170525').default as new (config: $OpenApiUtil.Config) => SmsClient;
  const { $OpenApiUtil } = require('@alicloud/openapi-core') as typeof import('@alicloud/openapi-core');
  const client = new Dysmsapi(
    new $OpenApiUtil.Config({
      accessKeyId: context.config.sms.aliyun.accessKeyId,
      accessKeySecret: context.config.sms.aliyun.accessKeySecret,
      endpoint: 'dysmsapi.aliyuncs.com',
    }),
  );
  const response = await client.sendSms({
    phoneNumbers: phone,
    signName: context.config.sms.aliyun.signName,
    templateCode: context.config.sms.aliyun.templateCode,
    templateParam: JSON.stringify({ code }),
  } as SendSmsRequest);
  if (response.body?.code !== 'OK') throw new Error(response.body?.message ?? '短信发送失败');
}
