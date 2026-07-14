import assert from 'node:assert/strict';
import test from 'node:test';
import { loadConfig } from '../src/config.js';

const productionBase: NodeJS.ProcessEnv = {
  NODE_ENV: 'production',
  JWT_ACCESS_SECRET: 'production-access-secret',
  JWT_REFRESH_SECRET: 'production-refresh-secret',
  SMS_PROVIDER: 'aliyun',
  ALIYUN_SMS_ACCESS_KEY_ID: 'sms-access',
  ALIYUN_SMS_ACCESS_KEY_SECRET: 'sms-secret',
  ALIYUN_SMS_SIGN_NAME: 'Lover',
  ALIYUN_SMS_TEMPLATE_CODE: 'SMS_123',
};

test('production fails fast when storage is local', () => {
  assert.throws(
    () => loadConfig({ ...productionBase, STORAGE_PROVIDER: 'local' }),
    /STORAGE_PROVIDER must be qiniu/,
  );
});

test('qiniu selection fails fast when any required setting is missing', () => {
  assert.throws(
    () => loadConfig({ NODE_ENV: 'test', STORAGE_PROVIDER: 'qiniu', QINIU_ACCESS_KEY: 'access' }),
    /QINIU_SECRET_KEY.*QINIU_BUCKET.*QINIU_DOWNLOAD_DOMAIN/,
  );
});

test('complete production qiniu configuration is accepted and normalized', () => {
  const config = loadConfig({
    ...productionBase,
    STORAGE_PROVIDER: 'qiniu',
    QINIU_ACCESS_KEY: 'qiniu-access',
    QINIU_SECRET_KEY: 'qiniu-secret',
    QINIU_BUCKET: 'lover-private',
    QINIU_UPLOAD_URL: 'https://upload.qiniup.com/',
    QINIU_DOWNLOAD_DOMAIN: 'private.example.com/',
  });
  assert.equal(config.storage.provider, 'qiniu');
  assert.equal(config.storage.qiniu.uploadUrl, 'https://upload.qiniup.com');
  assert.equal(config.storage.qiniu.downloadDomain, 'https://private.example.com');
});
