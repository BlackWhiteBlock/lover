/**
 * 将 media_assets 中 provider=local 且 status=ready 的文件迁到七牛，并更新数据库记录。
 *
 * 每条资产在改库前会：上传 → 七牛 stat 校验 → 签发下载 URL → HTTP 实测可访问。
 * 默认保留本地文件；确认 App 读图正常后再手动删本地，或加 --delete-local。
 *
 * 用法（在 backend 目录，已配置好七牛环境变量）：
 *   npx tsx scripts/migrate-local-to-qiniu.ts                 # dry-run
 *   npx tsx scripts/migrate-local-to-qiniu.ts --apply         # 上传+验读+改库，保留本地
 *   npx tsx scripts/migrate-local-to-qiniu.ts --apply --delete-local
 *   npx tsx scripts/migrate-local-to-qiniu.ts --apply --limit=20
 *   npx tsx scripts/migrate-local-to-qiniu.ts --verify        # 仅校验已是 qiniu 的资产能否下载
 */
import fs from 'node:fs/promises';
import pg from 'pg';
import qiniu from 'qiniu';
import { loadConfig } from '../src/config.js';
import { resolveLocalObjectPath } from '../src/storage/local.js';
import { QiniuStorageProvider } from '../src/storage/provider.js';

type AssetRow = {
  id: string;
  object_key: string;
  mime_type: string;
  size_bytes: string | null;
  expected_size: string;
  provider: 'local' | 'qiniu';
};

function argFlag(name: string): boolean {
  return process.argv.includes(name);
}

function argValue(name: string): string | undefined {
  const prefix = `${name}=`;
  const hit = process.argv.find((item) => item.startsWith(prefix));
  return hit?.slice(prefix.length);
}

function putFile(
  mac: qiniu.auth.digest.Mac,
  bucket: string,
  objectKey: string,
  localPath: string,
  mimeType: string,
): Promise<{ hash?: string; size?: number }> {
  const conf = new qiniu.conf.Config();
  const formUploader = new qiniu.form_up.FormUploader(conf);
  const putExtra = new qiniu.form_up.PutExtra();
  putExtra.mimeType = mimeType;
  const policy = new qiniu.rs.PutPolicy({
    scope: `${bucket}:${objectKey}`,
    insertOnly: 0,
  });
  const token = policy.uploadToken(mac);

  return new Promise((resolve, reject) => {
    formUploader.putFile(token, objectKey, localPath, putExtra, (err, body, info) => {
      if (err) {
        reject(err);
        return;
      }
      if (info.statusCode !== 200) {
        reject(new Error(`七牛上传失败 HTTP ${info.statusCode}: ${JSON.stringify(body)}`));
        return;
      }
      resolve({
        hash: typeof body?.hash === 'string' ? body.hash : undefined,
        size: typeof body?.fsize === 'number' ? body.fsize : undefined,
      });
    });
  });
}

/** 用与线上一致的签名逻辑签发 URL，并 HTTP 实测可读（HEAD，失败再 Range GET）。 */
async function assertReadableFromQiniu(
  storage: QiniuStorageProvider,
  objectKey: string,
  mimeType: string,
  expectedMinBytes: number,
): Promise<{ url: string; bytes: number }> {
  const stat = await storage.statObject(objectKey);
  if (stat.sizeBytes <= 0) {
    throw new Error(`七牛对象大小无效: ${objectKey}`);
  }
  if (expectedMinBytes > 0 && Math.abs(stat.sizeBytes - expectedMinBytes) > Math.max(1024, expectedMinBytes * 0.05)) {
    throw new Error(
      `七牛对象大小与本地不一致: ${objectKey} local=${expectedMinBytes} remote=${stat.sizeBytes}`,
    );
  }

  const variant = mimeType.startsWith('image/') ? 'thumb' : 'original';
  const signed = await storage.signDownload(objectKey, mimeType, variant);
  const bytes = await probeDownload(signed.url);
  if (bytes <= 0) {
    throw new Error(`签名下载无内容: ${objectKey}`);
  }
  return { url: signed.url, bytes };
}

async function probeDownload(url: string): Promise<number> {
  const head = await fetch(url, { method: 'HEAD', redirect: 'follow' });
  if (head.ok) {
    const len = Number(head.headers.get('content-length') ?? '0');
    if (len > 0) return len;
  }

  const get = await fetch(url, {
    method: 'GET',
    redirect: 'follow',
    headers: { Range: 'bytes=0-0' },
  });
  if (!get.ok && get.status !== 206) {
    const body = await get.text().catch(() => '');
    throw new Error(`签名下载失败 HTTP ${get.status}: ${body.slice(0, 200)}`);
  }
  const buf = Buffer.from(await get.arrayBuffer());
  if (buf.byteLength <= 0) {
    throw new Error(`签名下载空响应 HTTP ${get.status}`);
  }
  const total = Number(get.headers.get('content-range')?.split('/')[1] ?? get.headers.get('content-length') ?? buf.byteLength);
  return total > 0 ? total : buf.byteLength;
}

const apply = argFlag('--apply');
const verifyOnly = argFlag('--verify');
const deleteLocal = argFlag('--delete-local');
const keepLocal = argFlag('--keep-local') || !deleteLocal;
const limit = Number(argValue('--limit') ?? '0');

if (apply && verifyOnly) {
  throw new Error('不能同时使用 --apply 与 --verify');
}

const config = loadConfig();
if (config.storage.provider !== 'qiniu') {
  throw new Error('请先将 STORAGE_PROVIDER=qiniu 并配置完整 QINIU_*，再运行迁移');
}

const mac = new qiniu.auth.digest.Mac(
  config.storage.qiniu.accessKey,
  config.storage.qiniu.secretKey,
);
const bucket = config.storage.qiniu.bucket;
const storage = new QiniuStorageProvider(config);
const pool = new pg.Pool({ connectionString: config.databaseUrl });

const migrateSql = `
  select id, object_key, mime_type, size_bytes, expected_size, provider
  from media_assets
  where provider = 'local' and status = 'ready'
  order by created_at asc
  ${limit > 0 ? `limit ${Math.floor(limit)}` : ''}
`;

const verifySql = `
  select id, object_key, mime_type, size_bytes, expected_size, provider
  from media_assets
  where provider = 'qiniu' and status = 'ready' and bucket = $1
  order by created_at asc
  ${limit > 0 ? `limit ${Math.floor(limit)}` : ''}
`;

try {
  if (verifyOnly) {
    const { rows } = await pool.query<AssetRow>(verifySql, [bucket]);
    console.info(`[migrate-local-to-qiniu] mode=VERIFY count=${rows.length} bucket=${bucket}`);
    console.info(`downloadDomain=${config.storage.qiniu.downloadDomain}`);
    let ok = 0;
    let failed = 0;
    for (const row of rows) {
      try {
        const expected = Number(row.size_bytes ?? row.expected_size ?? 0);
        const probed = await assertReadableFromQiniu(storage, row.object_key, row.mime_type, expected);
        ok += 1;
        console.info(`[ok] ${row.id} ${row.object_key} readable (~${probed.bytes} bytes)`);
      } catch (error) {
        failed += 1;
        console.error(`[fail] ${row.id} ${row.object_key}`, error);
      }
    }
    console.info(`[done] ok=${ok} failed=${failed}`);
    if (failed === 0 && rows.length > 0) {
      console.info('全部已迁资产可通过七牛签名 URL 读取，可以安全删除本地 uploads。');
    }
    process.exitCode = failed > 0 ? 1 : 0;
  } else {
    const { rows } = await pool.query<AssetRow>(migrateSql);
    console.info(
      `[migrate-local-to-qiniu] mode=${apply ? 'APPLY' : 'DRY-RUN'} count=${rows.length} bucket=${bucket} keepLocal=${keepLocal}`,
    );
    console.info(`downloadDomain=${config.storage.qiniu.downloadDomain}`);
    if (!rows.length) {
      console.info('没有需要迁移的本地资产。可用 --verify 检查已迁到七牛的资产。');
      process.exitCode = 0;
    } else {
      let ok = 0;
      let skipped = 0;
      let failed = 0;

      for (const row of rows) {
        const localPath = resolveLocalObjectPath(config.storage.dir, row.object_key);
        let stat;
        try {
          stat = await fs.stat(localPath);
        } catch {
          console.warn(`[skip] ${row.id} 本地文件不存在: ${localPath}`);
          skipped += 1;
          continue;
        }
        if (!stat.isFile() || stat.size <= 0) {
          console.warn(`[skip] ${row.id} 本地文件无效: ${localPath}`);
          skipped += 1;
          continue;
        }

        console.info(
          `[${apply ? 'upload' : 'would-upload'}] ${row.id} ${row.object_key} (${stat.size} bytes, ${row.mime_type})`,
        );

        if (!apply) {
          ok += 1;
          continue;
        }

        try {
          const uploaded = await putFile(mac, bucket, row.object_key, localPath, row.mime_type);
          const probed = await assertReadableFromQiniu(
            storage,
            row.object_key,
            row.mime_type,
            uploaded.size ?? stat.size,
          );
          const sizeBytes = uploaded.size ?? probed.bytes ?? stat.size;

          const updated = await pool.query(
            `update media_assets
             set provider = 'qiniu',
                 bucket = $2,
                 size_bytes = $3,
                 object_hash = coalesce($4, object_hash),
                 expected_size = $3
             where id = $1 and provider = 'local'`,
            [row.id, bucket, sizeBytes, uploaded.hash ?? null],
          );
          if (!updated.rowCount) {
            throw new Error('数据库未更新（可能已被并发修改）');
          }

          if (!keepLocal) {
            await fs.rm(localPath, { force: true });
          }
          ok += 1;
          console.info(`[ok] ${row.id} verified download (~${probed.bytes} bytes)`);
        } catch (error) {
          failed += 1;
          console.error(`[fail] ${row.id}`, error);
        }
      }

      console.info(`[done] ok=${ok} skipped=${skipped} failed=${failed}`);
      if (!apply) {
        console.info('这是 dry-run。确认无误后加上 --apply 执行迁移（默认保留本地文件）。');
      } else if (failed === 0 && ok > 0) {
        console.info(
          keepLocal
            ? '迁移完成且已验读。建议再跑 --verify，确认 App 正常后手动删本地 uploads。'
            : '迁移完成且已验读，本地文件已按 --delete-local 删除。',
        );
      }
      process.exitCode = failed > 0 ? 1 : 0;
    }
  }
} finally {
  await pool.end();
}
