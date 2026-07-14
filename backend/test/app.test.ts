import assert from 'node:assert/strict';
import test from 'node:test';
import jwt from 'jsonwebtoken';
import { buildApp } from '../src/app.js';
import { loadConfig } from '../src/config.js';
import type { Database } from '../src/db.js';

function fakeDatabase(rows: unknown[] = []) {
  return {
    query: async () => ({ rows, rowCount: rows.length }),
  } as unknown as Database;
}

test('Fastify application boots and serves health', async () => {
  const config = loadConfig({
    NODE_ENV: 'test',
    LOG_LEVEL: 'silent',
    STORAGE_DIR: './data/test-uploads',
  });
  const app = await buildApp(config, fakeDatabase([{ ok: 1 }]));
  try {
    const response = await app.inject({ method: 'GET', url: '/health' });
    assert.equal(response.statusCode, 200);
    assert.deepEqual(response.json(), {
      ok: true,
      service: 'lover-backend',
      checks: { database: 'up' },
    });
  } finally {
    await app.close();
  }
});

test('health and readiness fail when the database probe fails', async () => {
  const database = {
    query: async () => { throw new Error('database down'); },
  } as unknown as Database;
  const app = await buildApp(loadConfig({ NODE_ENV: 'test', LOG_LEVEL: 'silent' }), database);
  try {
    for (const url of ['/health', '/ready']) {
      const response = await app.inject({ method: 'GET', url });
      assert.equal(response.statusCode, 503);
      assert.equal(response.json().checks.database, 'down');
    }
  } finally {
    await app.close();
  }
});

test('local signed media URL still requires current active membership', async () => {
  const config = loadConfig({ NODE_ENV: 'test', LOG_LEVEL: 'silent' });
  const app = await buildApp(config, fakeDatabase([]));
  const assetId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
  const token = jwt.sign({
    type: 'download',
    assetId,
    objectKey: 'couples/space-a/2026-07/file.jpg',
    spaceId: 'space-a',
    sub: 'user-a',
  }, config.storage.signingSecret, { expiresIn: 60 });
  try {
    const response = await app.inject({
      method: 'GET',
      url: `/private-media/${assetId}?token=${encodeURIComponent(token)}`,
    });
    assert.equal(response.statusCode, 403);
    assert.equal(response.json().error.code, 'FORBIDDEN');
  } finally {
    await app.close();
  }
});

test('qiniu sign endpoint cannot sign an asset outside the active space', async () => {
  const config = loadConfig({
    NODE_ENV: 'test',
    LOG_LEVEL: 'silent',
    STORAGE_PROVIDER: 'qiniu',
    QINIU_ACCESS_KEY: 'access',
    QINIU_SECRET_KEY: 'secret',
    QINIU_BUCKET: 'private',
    QINIU_DOWNLOAD_DOMAIN: 'private.example.com',
  });
  const database = {
    query: async (text: string) => {
      if (text.includes('from users u join auth_sessions')) {
        return {
          rowCount: 1,
          rows: [{ id: 'user-a', phone: '13800138000', nickname: 'A', avatarUrl: null }],
        };
      }
      if (text.includes('from couple_members where user_id')) {
        return { rowCount: 1, rows: [{ space_id: 'space-a' }] };
      }
      return { rowCount: 0, rows: [] };
    },
  } as unknown as Database;
  const app = await buildApp(config, database);
  const accessToken = jwt.sign(
    { sub: 'user-a', sid: 'session-a', type: 'access' },
    config.jwt.accessSecret,
    { expiresIn: 60 },
  );
  try {
    const response = await app.inject({
      method: 'POST',
      url: '/api/media-assets/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/sign',
      headers: { authorization: `Bearer ${accessToken}` },
      payload: { variant: 'thumb' },
    });
    assert.equal(response.statusCode, 404);
    assert.equal(response.json().error.code, 'NOT_FOUND');
  } finally {
    await app.close();
  }
});

test('qiniu complete looks up the exact asset owner and active space before stat', async () => {
  const config = loadConfig({
    NODE_ENV: 'test',
    LOG_LEVEL: 'silent',
    STORAGE_PROVIDER: 'qiniu',
    QINIU_ACCESS_KEY: 'access',
    QINIU_SECRET_KEY: 'secret',
    QINIU_BUCKET: 'private',
    QINIU_DOWNLOAD_DOMAIN: 'private.example.com',
  });
  const assetId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
  let checkedBoundary = false;
  const database = {
    query: async (text: string, values?: unknown[]) => {
      if (text.includes('from users u join auth_sessions')) {
        return {
          rowCount: 1,
          rows: [{ id: 'user-a', phone: '13800138000', nickname: 'A', avatarUrl: null }],
        };
      }
      if (text.includes('from couple_members where user_id')) {
        return { rowCount: 1, rows: [{ space_id: 'space-a' }] };
      }
      if (text.includes('from media_assets') && text.includes('owner_id = $3')) {
        assert.deepEqual(values, [assetId, 'space-a', 'user-a']);
        checkedBoundary = true;
        return { rowCount: 0, rows: [] };
      }
      throw new Error('Unexpected database query');
    },
  } as unknown as Database;
  const app = await buildApp(config, database);
  const accessToken = jwt.sign(
    { sub: 'user-a', sid: 'session-a', type: 'access' },
    config.jwt.accessSecret,
    { expiresIn: 60 },
  );
  try {
    const response = await app.inject({
      method: 'POST',
      url: '/api/media-assets/complete',
      headers: { authorization: `Bearer ${accessToken}` },
      payload: { assetId },
    });
    assert.equal(response.statusCode, 404);
    assert.equal(checkedBoundary, true);
  } finally {
    await app.close();
  }
});

test('login endpoint is API-rate-limited', async () => {
  const app = await buildApp(
    loadConfig({ NODE_ENV: 'test', LOG_LEVEL: 'silent' }),
    fakeDatabase([]),
  );
  try {
    let response;
    for (let index = 0; index < 11; index += 1) {
      response = await app.inject({ method: 'POST', url: '/api/auth/sms/login', payload: {} });
    }
    assert.equal(response!.statusCode, 429);
    assert.equal(response!.json().error.code, 'RATE_LIMITED');
  } finally {
    await app.close();
  }
});
