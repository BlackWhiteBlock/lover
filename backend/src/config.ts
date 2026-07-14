import path from 'node:path';
import 'dotenv/config';
import { z } from 'zod';

const booleanString = z.enum(['true', 'false']).transform((value) => value === 'true');

const envSchema = z.object({
  NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
  HOST: z.string().default('0.0.0.0'),
  PORT: z.coerce.number().int().min(1).max(65535).default(4000),
  DATABASE_URL: z.string().min(1).default('postgres://postgres:postgres@localhost:5432/lover'),
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
  STORAGE_PROVIDER: z.enum(['local']).default('local'),
  STORAGE_DIR: z.string().default(path.resolve(process.cwd(), 'data/uploads')),
  STORAGE_SIGNING_SECRET: z.string().min(16).default('dev-storage-secret-change-me'),
  STORAGE_TOKEN_TTL_SECONDS: z.coerce.number().int().min(30).max(3600).default(600),
  PUBLIC_BASE_URL: z.string().url().default('http://localhost:4000'),
  CORS_ORIGIN: z.string().default('http://localhost:5173'),
  LOG_LEVEL: z.string().default('info'),
  TRUST_PROXY: booleanString.default(false),
});

export type Config = ReturnType<typeof loadConfig>;

export function loadConfig(env: NodeJS.ProcessEnv = process.env) {
  const value = envSchema.parse(env);
  if (value.NODE_ENV === 'production') {
    const missing: string[] = [];
    if (value.SMS_PROVIDER === 'dev') missing.push('SMS_PROVIDER must not be dev');
    if (value.STORAGE_PROVIDER === 'local') missing.push('STORAGE_PROVIDER local is forbidden');
    if (value.JWT_ACCESS_SECRET.startsWith('dev-')) missing.push('JWT_ACCESS_SECRET');
    if (value.JWT_REFRESH_SECRET.startsWith('dev-')) missing.push('JWT_REFRESH_SECRET');
    if (value.STORAGE_SIGNING_SECRET.startsWith('dev-')) missing.push('STORAGE_SIGNING_SECRET');
    if (value.SMS_PROVIDER === 'aliyun') {
      if (!value.ALIYUN_SMS_ACCESS_KEY_ID) missing.push('ALIYUN_SMS_ACCESS_KEY_ID');
      if (!value.ALIYUN_SMS_ACCESS_KEY_SECRET) missing.push('ALIYUN_SMS_ACCESS_KEY_SECRET');
      if (!value.ALIYUN_SMS_SIGN_NAME) missing.push('ALIYUN_SMS_SIGN_NAME');
      if (!value.ALIYUN_SMS_TEMPLATE_CODE) missing.push('ALIYUN_SMS_TEMPLATE_CODE');
    }
    if (missing.length) throw new Error(`Production configuration invalid: ${missing.join(', ')}`);
  }
  return {
    env: value.NODE_ENV,
    host: value.HOST,
    port: value.PORT,
    databaseUrl: value.DATABASE_URL,
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
    storage: {
      provider: value.STORAGE_PROVIDER,
      dir: path.resolve(value.STORAGE_DIR),
      signingSecret: value.STORAGE_SIGNING_SECRET,
      tokenTtlSeconds: value.STORAGE_TOKEN_TTL_SECONDS,
    },
    publicBaseUrl: value.PUBLIC_BASE_URL.replace(/\/+$/, ''),
    corsOrigin: value.CORS_ORIGIN,
    logLevel: value.LOG_LEVEL,
    trustProxy: value.TRUST_PROXY,
  } as const;
}
