import assert from 'node:assert/strict';
import test from 'node:test';
import { buildApp } from '../src/app.js';
import { loadConfig } from '../src/config.js';

test('Fastify application boots and serves health', async () => {
  const app = await buildApp(loadConfig({
    NODE_ENV: 'test',
    LOG_LEVEL: 'silent',
    STORAGE_DIR: './data/test-uploads',
  }));
  try {
    const response = await app.inject({ method: 'GET', url: '/health' });
    assert.equal(response.statusCode, 200);
    assert.deepEqual(response.json(), { ok: true, service: 'lover-backend' });
  } finally {
    await app.close();
  }
});
