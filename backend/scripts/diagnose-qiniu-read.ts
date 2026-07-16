/**
 * 诊断单条（或最近一条）七牛资产能否签名并 HTTP 读到。
 *
 *   npx tsx scripts/diagnose-qiniu-read.ts
 *   npx tsx scripts/diagnose-qiniu-read.ts --asset=708250b9-66f3-4fa9-b173-d017c0965888
 */
import pg from 'pg';
import { loadConfig } from '../src/config.js';
import { QiniuStorageProvider } from '../src/storage/provider.js';

function argValue(name: string): string | undefined {
  const prefix = `${name}=`;
  const hit = process.argv.find((item) => item.startsWith(prefix));
  return hit?.slice(prefix.length);
}

async function probe(url: string): Promise<{ status: number; bytes: number; contentType: string | null }> {
  const head = await fetch(url, { method: 'HEAD', redirect: 'follow' });
  if (head.ok) {
    return {
      status: head.status,
      bytes: Number(head.headers.get('content-length') ?? '0'),
      contentType: head.headers.get('content-type'),
    };
  }
  const get = await fetch(url, {
    method: 'GET',
    redirect: 'follow',
    headers: { Range: 'bytes=0-0' },
  });
  const buf = Buffer.from(await get.arrayBuffer().catch(() => new ArrayBuffer(0)));
  return {
    status: get.status,
    bytes: buf.byteLength,
    contentType: get.headers.get('content-type'),
  };
}

const config = loadConfig();
if (config.storage.provider !== 'qiniu') {
  throw new Error(`当前 STORAGE_PROVIDER=${config.storage.provider}，不是 qiniu`);
}

const storage = new QiniuStorageProvider(config);
const pool = new pg.Pool({ connectionString: config.databaseUrl });
const assetId = argValue('--asset');

try {
  const { rows } = await pool.query<{
    id: string;
    object_key: string;
    mime_type: string;
    provider: string;
    bucket: string | null;
    space_id: string;
    status: string;
  }>(
    assetId
      ? `select id, object_key, mime_type, provider, bucket, space_id, status
         from media_assets where id = $1`
      : `select id, object_key, mime_type, provider, bucket, space_id, status
         from media_assets
         where provider = 'qiniu' and status = 'ready' and mime_type like 'image/%'
         order by created_at desc limit 1`,
    assetId ? [assetId] : [],
  );
  const row = rows[0];
  if (!row) {
    console.error('没有找到可诊断的资产');
    process.exitCode = 1;
  } else {
    console.info('--- env ---');
    console.info(`STORAGE_PROVIDER=${config.storage.provider}`);
    console.info(`QINIU_BUCKET(env)=${config.storage.qiniu.bucket}`);
    console.info(`QINIU_DOWNLOAD_DOMAIN=${config.storage.qiniu.downloadDomain}`);
    console.info(`QINIU_IMAGE_THUMB_FOP=${config.storage.qiniu.imageThumbFop}`);
    console.info('--- asset ---');
    console.info(row);

    const bucketOk = row.provider === 'qiniu' && row.bucket === config.storage.qiniu.bucket;
    console.info(`bucketMatch=${bucketOk} (DB bucket 必须与 env 完全一致，否则 App 会 STORAGE_PROVIDER_MISMATCH)`);

    if (row.status !== 'ready' || row.provider !== 'qiniu') {
      console.error('资产不是 ready/qiniu，无法按七牛验读');
      process.exitCode = 1;
    } else {
      try {
        const stat = await storage.statObject(row.object_key);
        console.info(`qiniuStat ok size=${stat.sizeBytes} mime=${stat.mimeType}`);
      } catch (error) {
        console.error('qiniuStat FAILED（Bucket 里可能没有这个 key，或 AK/SK/Bucket 不对）', error);
        process.exitCode = 1;
      }

      for (const variant of ['original', 'thumb'] as const) {
        try {
          const signed = await storage.signDownload(row.object_key, row.mime_type, variant);
          const probed = await probe(signed.url);
          const ok = probed.status === 200 || probed.status === 206;
          console.info(
            `[${variant}] http=${probed.status} bytes=${probed.bytes} contentType=${probed.contentType} ok=${ok}`,
          );
          console.info(`[${variant}] url=${signed.url.slice(0, 160)}...`);
          if (!ok) process.exitCode = 1;
        } catch (error) {
          console.error(`[${variant}] FAILED`, error);
          process.exitCode = 1;
        }
      }

      if (!config.storage.qiniu.downloadDomain.startsWith('https://')) {
        console.warn('WARNING: 下载域不是 https。Release 包默认禁止明文 HTTP，App 里会表现为图片全白。');
      }
      if (/blur\/\d+x0(?:\/|$)/.test(config.storage.qiniu.imageThumbFop)) {
        console.warn('WARNING: FOP 含 blur/…x0，七牛可能处理失败；建议去掉 blur 段。');
      }
    }
  }
} finally {
  await pool.end();
}
