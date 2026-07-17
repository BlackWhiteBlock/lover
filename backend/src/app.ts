import fs from 'node:fs/promises';
import cors from '@fastify/cors';
import multipart from '@fastify/multipart';
import rateLimit from '@fastify/rate-limit';
import swagger from '@fastify/swagger';
import swaggerUi from '@fastify/swagger-ui';
import Fastify from 'fastify';
import type { Config } from './config.js';
import { createDatabase, type Database } from './db.js';
import { AppError, errorHandler } from './errors.js';
import { openApiDocument } from './openapi.js';
import { createAuthHandler, registerAuth } from './modules/auth.js';
import { registerActivity } from './modules/activity.js';
import { registerCouples } from './modules/couples.js';
import { registerAssets } from './modules/assets.js';
import { registerMedia } from './modules/media.js';
import { registerAnniversaries } from './modules/anniversaries.js';
import { registerLetters } from './modules/letters.js';
import { registerBootstrap } from './modules/bootstrap.js';
import { registerQuotes } from './modules/quotes.js';

export async function buildApp(config: Config, database?: Database) {
  const db = database ?? createDatabase(config);
  const app = Fastify({
    logger: { level: config.logLevel },
    trustProxy: config.trustProxy,
  });
  const context = { config, db };

  await app.register(cors, {
    origin: config.corsOrigin.split(',').map((origin) => origin.trim()),
    credentials: true,
  });
  await app.register(multipart, {
    limits: { files: 1, fileSize: 300 * 1024 * 1024 },
  });
  await app.register(rateLimit, {
    global: false,
    errorResponseBuilder: (_request, context) => new AppError(
      429,
      'RATE_LIMITED',
      '请求过于频繁，请稍后重试',
      { retryAfterSeconds: Math.ceil(context.ttl / 1000) },
    ),
  });
  await app.register(swagger, {
    mode: 'static',
    specification: { document: openApiDocument as never },
  });
  await app.register(swaggerUi, { routePrefix: '/docs' });
  if (config.storage.provider === 'local') await fs.mkdir(config.storage.dir, { recursive: true });

  app.setErrorHandler(errorHandler);
  app.setNotFoundHandler((_request, reply) => reply.code(404).send({
    error: { code: 'ROUTE_NOT_FOUND', message: '接口不存在' },
  }));

  app.get('/health', async (_request, reply) => {
    try {
      await db.query('select 1');
      return { ok: true, service: 'lover-backend', checks: { database: 'up' } };
    } catch {
      return reply.code(503).send({
        ok: false,
        service: 'lover-backend',
        checks: { database: 'down' },
        error: { code: 'DATABASE_UNAVAILABLE', message: '数据库不可用' },
      });
    }
  });
  app.get('/ready', async (_request, reply) => {
    try {
      await db.query('select 1');
      return { ok: true, checks: { database: 'up' } };
    } catch {
      return reply.code(503).send({
        ok: false,
        checks: { database: 'down' },
        error: { code: 'NOT_READY', message: '数据库不可用' },
      });
    }
  });
  app.get('/openapi.json', async () => openApiDocument);

  const auth = createAuthHandler(context);
  registerAuth(app, context);
  registerCouples(app, context, auth);
  registerAssets(app, context, auth);
  registerMedia(app, context, auth);
  registerAnniversaries(app, context, auth);
  registerLetters(app, context, auth);
  registerQuotes(app, context, auth);
  registerBootstrap(app, context, auth);
  registerActivity(app, context, auth);

  app.addHook('onClose', async () => {
    if (!database) await db.close();
  });
  return app;
}
