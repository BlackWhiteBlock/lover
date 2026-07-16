import { buildApp } from './app.js';
import { loadConfig } from './config.js';

const config = loadConfig();
const app = await buildApp(config);

try {
  await app.listen({ host: config.host, port: config.port });
  app.log.info(
    {
      storageProvider: config.storage.provider,
      qiniuBucket: config.storage.provider === 'qiniu' ? config.storage.qiniu.bucket : undefined,
    },
    'storage backend ready',
  );
} catch (error) {
  app.log.error(error);
  process.exitCode = 1;
  await app.close();
}
