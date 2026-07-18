/**
 * 资产清理脚本
 *
 * 清理以下三类孤儿/过期资产：
 * 1. status = 'pending' 且超过 24 小时未 complete 的上传记录
 * 2. 媒体编辑中移除的旧 asset（不再被 media_item_assets 引用）
 * 3. 用户头像/情侣合照替换后的旧 asset
 *
 * 用法: npx tsx scripts/cleanup-orphan-assets.ts [--dry-run]
 */
import pg from 'pg';
import { loadConfig } from '../src/config.js';
import { createStorageProvider } from '../src/storage/provider.js';

const { Pool } = pg;

const DRY_RUN = process.argv.includes('--dry-run');
const PENDING_TTL_HOURS = 24;

async function main() {
  const config = loadConfig();
  const db = new Pool({ connectionString: config.databaseUrl });
  const provider = createStorageProvider(config);

  console.log(`[cleanup] provider=${provider.name} dryRun=${DRY_RUN}`);

  // 1. 清理超期 pending 资产
  const pendingResult = await db.query(
    `SELECT id, object_key FROM media_assets
     WHERE status = 'pending' AND created_at < now() - interval '${PENDING_TTL_HOURS} hours'`,
  );
  console.log(`[cleanup] Found ${pendingResult.rowCount} expired pending assets`);
  for (const row of pendingResult.rows) {
    console.log(`  - ${row.id} (${row.object_key})`);
    if (!DRY_RUN) {
      await provider.deleteObject(row.object_key).catch((e) => console.warn(`    storage delete failed: ${e.message}`));
      await db.query(`DELETE FROM media_assets WHERE id = $1`, [row.id]);
    }
  }

  // 2. 清理不再被引用的 asset（不在 media_item_assets、users.avatar_asset_id、users.couple_cover_asset_id、anniversaries.cover_asset_id 中）
  const orphanResult = await db.query(
    `SELECT ma.id, ma.object_key FROM media_assets ma
     WHERE ma.status = 'completed'
       AND NOT EXISTS (SELECT 1 FROM media_item_assets mia WHERE mia.asset_id = ma.id)
       AND NOT EXISTS (SELECT 1 FROM users u WHERE u.avatar_asset_id = ma.id)
       AND NOT EXISTS (SELECT 1 FROM users u WHERE u.couple_cover_asset_id = ma.id)
       AND NOT EXISTS (SELECT 1 FROM anniversaries a WHERE a.cover_asset_id = ma.id)
       AND ma.created_at < now() - interval '1 hour'`,
  );
  console.log(`[cleanup] Found ${orphanResult.rowCount} orphaned completed assets`);
  for (const row of orphanResult.rows) {
    console.log(`  - ${row.id} (${row.object_key})`);
    if (!DRY_RUN) {
      await provider.deleteObject(row.object_key).catch((e) => console.warn(`    storage delete failed: ${e.message}`));
      await db.query(`DELETE FROM media_assets WHERE id = $1`, [row.id]);
    }
  }

  console.log(`[cleanup] Done.`);
  await db.end();
}

main().catch((err) => {
  console.error('[cleanup] Fatal error:', err);
  process.exit(1);
});
