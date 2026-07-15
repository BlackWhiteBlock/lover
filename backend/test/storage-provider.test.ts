import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import test from 'node:test';
import { loadConfig } from '../src/config.js';
import {
  createStorageProvider,
  QiniuStorageProvider,
  type QiniuManager,
} from '../src/storage/provider.js';
import {
  assertObjectKeyBelongsToSpace,
  assertSafeObjectKey,
  buildObjectKey,
} from '../src/storage/object-key.js';
import { validateStoredObject } from '../src/modules/assets.js';
import { orderedMigrationNames } from '../src/migrations.js';

const spaceId = '11111111-1111-4111-8111-111111111111';
const qiniuConfig = loadConfig({
  NODE_ENV: 'test',
  STORAGE_PROVIDER: 'qiniu',
  QINIU_ACCESS_KEY: 'test-access',
  QINIU_SECRET_KEY: 'test-secret',
  QINIU_BUCKET: 'lover-private',
  QINIU_DOWNLOAD_DOMAIN: 'private.example.com',
  QINIU_IMAGE_THUMB_FOP: 'imageMogr2/thumbnail/800x/format/webp',
});

test('object keys are server-shaped, space-scoped, and traversal-safe', () => {
  const key = buildObjectKey(spaceId, '../../portrait.JPG', 'image/jpeg', new Date('2026-07-14T00:00:00Z'));
  assert.match(key, new RegExp(`^couples/${spaceId}/2026-07/[a-f0-9]{24}\\.jpg$`));
  assert.equal(assertObjectKeyBelongsToSpace(key, spaceId), key);
  assert.throws(() => assertObjectKeyBelongsToSpace(key, '22222222-2222-4222-8222-222222222222'), /does not belong/);
  for (const unsafe of ['../x', '/absolute', 'a//b', 'a/./b', 'a\\b', 'a?x=1', 'a#fragment']) {
    assert.throws(() => assertSafeObjectKey(unsafe), /Invalid object key/);
  }
});

test('provider factory selects local or qiniu', () => {
  assert.equal(createStorageProvider(loadConfig({ NODE_ENV: 'test' })).name, 'local');
  assert.equal(createStorageProvider(qiniuConfig).name, 'qiniu');
});

test('qiniu upload policy pins exact bucket:key, insert-only key, size, and MIME', async () => {
  const provider = new QiniuStorageProvider(qiniuConfig);
  const objectKey = `couples/${spaceId}/2026-07/file.jpg`;
  const grant = await provider.createUploadGrant({
    assetId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    objectKey,
    mimeType: 'image/jpeg',
    sizeBytes: 12345,
  });
  const encodedPolicy = grant.uploadToken.split(':')[2];
  assert.ok(encodedPolicy);
  const policy = JSON.parse(Buffer.from(encodedPolicy, 'base64url').toString('utf8'));
  assert.equal(policy.scope, `lover-private:${objectKey}`);
  assert.equal(policy.insertOnly, 1);
  assert.equal(policy.forceSaveKey, true);
  assert.equal(policy.saveKey, objectKey);
  assert.equal(policy.fsizeMin, 12345);
  assert.equal(policy.fsizeLimit, 12345);
  assert.equal(policy.mimeLimit, 'image/jpeg');
  assert.equal('endUser' in policy, false);
  assert.deepEqual(grant.uploadFields, { key: objectKey });
});

test('qiniu stat uses guaranteed size and MIME fields without requiring endUser', async () => {
  let signedKey = '';
  let statTarget: [string, string] | undefined;
  const manager = {
    stat: async (bucket: string, key: string) => {
      statTarget = [bucket, key];
      return {
        ok: () => true,
        data: {
          fsize: 99,
          mimeType: 'image/jpeg',
          hash: 'etag',
        },
      };
    },
    privateDownloadUrl: (_domain: string, key: string) => {
      signedKey = key;
      return `https://signed.example/${key}`;
    },
  } as unknown as QiniuManager;
  const provider = new QiniuStorageProvider(qiniuConfig, () => manager);
  const key = 'couples/space/file.jpg';
  assert.deepEqual(await provider.statObject(key), {
    sizeBytes: 99,
    mimeType: 'image/jpeg',
    hash: 'etag',
  });
  assert.deepEqual(statTarget, ['lover-private', key]);
  const result = await provider.signDownload(key, 'image/jpeg', 'thumb');
  assert.match(signedKey, /file\.jpg\?imageMogr2\/thumbnail\/800x\/format\/webp$/);
  assert.match(result.url, /^https:\/\/signed\.example\//);
});

test('completion validation rejects qiniu size and MIME mismatches', () => {
  const asset = { provider: 'qiniu' as const, expectedSize: 99, mimeType: 'image/jpeg' };
  const valid = { sizeBytes: 99, mimeType: 'image/jpeg' };
  assert.doesNotThrow(() => validateStoredObject(asset, valid));
  assert.throws(
    () => validateStoredObject(asset, { ...valid, sizeBytes: 100 }),
    (error: any) => error.code === 'SIZE_MISMATCH',
  );
  assert.throws(
    () => validateStoredObject(asset, { ...valid, mimeType: 'image/png' }),
    (error: any) => error.code === 'MIME_MISMATCH',
  );
});

test('local completion retains size validation', () => {
  assert.doesNotThrow(() => validateStoredObject({
    provider: 'local',
    expectedSize: 10,
    mimeType: 'image/jpeg',
  }, {
    sizeBytes: 10,
  }));
});

test('migration executor orders the real 001–003 migrations', async () => {
  const entries = await fs.readdir(new URL('../db/migrations/', import.meta.url));
  assert.deepEqual(orderedMigrationNames(entries), [
    '001_initial.sql',
    '002_qiniu_storage.sql',
    '003_media_item_assets.sql',
  ]);
});
