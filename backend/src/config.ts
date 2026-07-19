import path from 'node:path';
import 'dotenv/config';
import { z } from 'zod';

const booleanString = z.enum(['true', 'false']).transform((value) => value === 'true');

const envSchema = z.object({
  NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
  HOST: z.string().default('0.0.0.0'),
  PORT: z.coerce.number().int().min(1).max(65535).default(4000),
  DATABASE_URL: z.string().min(1).default('postgres://postgres:postgres@localhost:5432/lover'),
  DATABASE_TIMEOUT_MS: z.coerce.number().int().min(500).max(30000).default(5000),
  JWT_ACCESS_SECRET: z.string().min(16).default('dev-access-secret-change-me'),
  JWT_REFRESH_SECRET: z.string().min(16).default('dev-refresh-secret-change-me'),
  ACCESS_TOKEN_TTL: z.string().default('15m'),
  REFRESH_TOKEN_TTL_DAYS: z.coerce.number().int().min(1).max(365).default(30),
  SMS_PROVIDER: z.enum(['dev', 'aliyun']).default('dev'),
  SMS_DEV_CODE: z.string().regex(/^\d{6}$/).default('123456'),
  SMS_CODE_TTL_SECONDS: z.coerce.number().int().min(60).default(300),
  SMS_COOLDOWN_SECONDS: z.coerce.number().int().min(1).default(60),
  ALIYUN_SMS_ACCESS_KEY_ID: z.string().default(''),
  ALIYUN_SMS_ACCESS_KEY_SECRET: z.string().default(''),
  ALIYUN_SMS_SIGN_NAME: z.string().default(''),
  ALIYUN_SMS_TEMPLATE_CODE: z.string().default(''),
  // 号码认证（一键登录）：方案密钥下发给客户端；GetMobile 复用 ALIYUN_SMS_* AK
  PNVS_ENABLED: booleanString.default(false),
  PNVS_ANDROID_SDK_SECRET: z.string().default(''),
  PNVS_HARMONY_SDK_SECRET: z.string().default(''),
  STORAGE_PROVIDER: z.enum(['local', 'qiniu']).default('local'),
  STORAGE_DIR: z.string().default(path.resolve(process.cwd(), 'data/uploads')),
  STORAGE_SIGNING_SECRET: z.string().min(16).default('dev-storage-secret-change-me'),
  STORAGE_TOKEN_TTL_SECONDS: z.coerce.number().int().min(30).max(3600).default(600),
  QINIU_ACCESS_KEY: z.string().default(''),
  QINIU_SECRET_KEY: z.string().default(''),
  QINIU_BUCKET: z.string().default(''),
  QINIU_UPLOAD_URL: z.string().url().default('https://upload.qiniup.com'),
  QINIU_DOWNLOAD_DOMAIN: z.string().default(''),
  QINIU_DOWNLOAD_EXPIRES_SECONDS: z.coerce.number().int().min(60).max(86400).default(3600),
  QINIU_IMAGE_THUMB_FOP: z.string().default('imageMogr2/auto-orient/thumbnail/800x/format/webp/quality/75'),
  PUBLIC_BASE_URL: z.string().url().default('http://localhost:4000'),
  CORS_ORIGIN: z.string().default('http://localhost:5173'),
  LOG_LEVEL: z.string().default('info'),
  TRUST_PROXY: booleanString.default(false),
});

export type Config = ReturnType<typeof loadConfig>;

/**
 * 兼容误写的 STORAGE_DRIVER（历史/其他项目习惯）。
 * 正式变量名为 STORAGE_PROVIDER；仅当未设置 PROVIDER 时回退到 DRIVER。
 */
export function normalizeStorageEnv(env: NodeJS.ProcessEnv): NodeJS.ProcessEnv {
  const provider = env.STORAGE_PROVIDER?.trim();
  const driver = env.STORAGE_DRIVER?.trim();
  if (provider) return env;
  if (!driver) return env;
  return { ...env, STORAGE_PROVIDER: driver };
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env) {
  const value = envSchema.parse(normalizeStorageEnv(env));
  const qiniuMissing: string[] = [];
  if (value.STORAGE_PROVIDER === 'qiniu') {
    if (!value.QINIU_ACCESS_KEY) qiniuMissing.push('QINIU_ACCESS_KEY');
    if (!value.QINIU_SECRET_KEY) qiniuMissing.push('QINIU_SECRET_KEY');
    if (!value.QINIU_BUCKET) qiniuMissing.push('QINIU_BUCKET');
    if (!value.QINIU_DOWNLOAD_DOMAIN) qiniuMissing.push('QINIU_DOWNLOAD_DOMAIN');
    if (!value.QINIU_IMAGE_THUMB_FOP.trim()) qiniuMissing.push('QINIU_IMAGE_THUMB_FOP');
    if (value.QINIU_DOWNLOAD_DOMAIN) {
      try {
        const url = new URL(normalizeDomain(value.QINIU_DOWNLOAD_DOMAIN));
        if (!['http:', 'https:'].includes(url.protocol)) throw new Error();
      } catch {
        qiniuMissing.push('QINIU_DOWNLOAD_DOMAIN is invalid');
      }
    }
    if (/[\r\n?#]/.test(value.QINIU_IMAGE_THUMB_FOP)) qiniuMissing.push('QINIU_IMAGE_THUMB_FOP is invalid');
  }
  if (qiniuMissing.length) {
    throw new Error(`Qiniu configuration invalid: ${qiniuMissing.join(', ')}`);
  }
  if (value.NODE_ENV === 'production') {
    const missing: string[] = [];
    if (value.SMS_PROVIDER === 'dev') missing.push('SMS_PROVIDER must not be dev');
    if (value.STORAGE_PROVIDER !== 'qiniu') missing.push('STORAGE_PROVIDER must be qiniu');
    if (!value.QINIU_UPLOAD_URL.startsWith('https://')) missing.push('QINIU_UPLOAD_URL must use HTTPS');
    if (!normalizeDomain(value.QINIU_DOWNLOAD_DOMAIN).startsWith('https://')) {
      missing.push('QINIU_DOWNLOAD_DOMAIN must use HTTPS');
    }
    if (value.JWT_ACCESS_SECRET.startsWith('dev-')) missing.push('JWT_ACCESS_SECRET');
    if (value.JWT_REFRESH_SECRET.startsWith('dev-')) missing.push('JWT_REFRESH_SECRET');
    if (value.SMS_PROVIDER === 'aliyun') {
      if (!value.ALIYUN_SMS_ACCESS_KEY_ID) missing.push('ALIYUN_SMS_ACCESS_KEY_ID');
      if (!value.ALIYUN_SMS_ACCESS_KEY_SECRET) missing.push('ALIYUN_SMS_ACCESS_KEY_SECRET');
      if (!value.ALIYUN_SMS_SIGN_NAME) missing.push('ALIYUN_SMS_SIGN_NAME');
      if (!value.ALIYUN_SMS_TEMPLATE_CODE) missing.push('ALIYUN_SMS_TEMPLATE_CODE');
    }
    if (value.PNVS_ENABLED) {
      if (!value.ALIYUN_SMS_ACCESS_KEY_ID) missing.push('ALIYUN_SMS_ACCESS_KEY_ID (required for PNVS GetMobile)');
      if (!value.ALIYUN_SMS_ACCESS_KEY_SECRET) {
        missing.push('ALIYUN_SMS_ACCESS_KEY_SECRET (required for PNVS GetMobile)');
      }
      if (!value.PNVS_ANDROID_SDK_SECRET && !value.PNVS_HARMONY_SDK_SECRET) {
        missing.push('PNVS_ANDROID_SDK_SECRET or PNVS_HARMONY_SDK_SECRET');
      }
    }
    if (missing.length) throw new Error(`Production configuration invalid: ${missing.join(', ')}`);
  }
  return {
    env: value.NODE_ENV,
    host: value.HOST,
    port: value.PORT,
    databaseUrl: value.DATABASE_URL,
    databaseTimeoutMs: value.DATABASE_TIMEOUT_MS,
    jwt: {
      accessSecret: value.JWT_ACCESS_SECRET,
      refreshSecret: value.JWT_REFRESH_SECRET,
      accessTtl: value.ACCESS_TOKEN_TTL,
      refreshTtlDays: value.REFRESH_TOKEN_TTL_DAYS,
    },
    sms: {
      provider: value.SMS_PROVIDER,
      devCode: value.SMS_DEV_CODE,
      codeTtlSeconds: value.SMS_CODE_TTL_SECONDS,
      cooldownSeconds: value.SMS_COOLDOWN_SECONDS,
      aliyun: {
        accessKeyId: value.ALIYUN_SMS_ACCESS_KEY_ID,
        accessKeySecret: value.ALIYUN_SMS_ACCESS_KEY_SECRET,
        signName: value.ALIYUN_SMS_SIGN_NAME,
        templateCode: value.ALIYUN_SMS_TEMPLATE_CODE,
      },
    },
    pnvs: {
      enabled: value.PNVS_ENABLED,
      androidSdkSecret: value.PNVS_ANDROID_SDK_SECRET,
      harmonySdkSecret: value.PNVS_HARMONY_SDK_SECRET,
    },
    storage: {
      provider: value.STORAGE_PROVIDER,
      dir: path.resolve(value.STORAGE_DIR),
      signingSecret: value.STORAGE_SIGNING_SECRET,
      tokenTtlSeconds: value.STORAGE_TOKEN_TTL_SECONDS,
      qiniu: {
        accessKey: value.QINIU_ACCESS_KEY,
        secretKey: value.QINIU_SECRET_KEY,
        bucket: value.QINIU_BUCKET,
        uploadUrl: value.QINIU_UPLOAD_URL.replace(/\/+$/, ''),
        downloadDomain: normalizeDomain(value.QINIU_DOWNLOAD_DOMAIN),
        downloadExpiresSeconds: value.QINIU_DOWNLOAD_EXPIRES_SECONDS,
        imageThumbFop: value.QINIU_IMAGE_THUMB_FOP.replace(/^\?/, ''),
      },
    },
    publicBaseUrl: value.PUBLIC_BASE_URL.replace(/\/+$/, ''),
    corsOrigin: value.CORS_ORIGIN,
    logLevel: value.LOG_LEVEL,
    trustProxy: value.TRUST_PROXY,
  } as const;
}

function normalizeDomain(value: string) {
  const domain = value.trim().replace(/\/+$/, '');
  if (!domain) return '';
  return domain.startsWith('http://') || domain.startsWith('https://') ? domain : `https://${domain}`;
}
