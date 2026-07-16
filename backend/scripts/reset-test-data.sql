-- 清空测试库业务数据，保留表结构与 schema_migrations（迁移历史）。
-- 执行后库处于「刚跑完迁移、尚无任何用户/内容」的可用初始状态。
--
-- 用法（请确认连的是测试库！）：
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f backend/scripts/reset-test-data.sql
--
-- 注意：
-- 1. 不会删除 schema_migrations，无需重新 migrate。
-- 2. 本地磁盘 / 七牛上的物理文件不会随本脚本删除，需另行清理（如有）。

BEGIN;

-- 先断开用户对资产/空间的引用，避免部分环境下 TRUNCATE 顺序受阻
UPDATE users
SET avatar_asset_id = NULL,
    couple_cover_asset_id = NULL,
    personal_space_id = NULL;

-- 同语句一次性清空所有业务表；CASCADE 处理相互外键
TRUNCATE TABLE
  media_item_assets,
  media_items,
  anniversaries,
  letters,
  unbinding_requests,
  couple_invites,
  couple_bind_requests,
  couple_links,
  couple_members,
  media_assets,
  auth_sessions,
  sms_codes,
  users,
  couple_spaces
RESTART IDENTITY CASCADE;

COMMIT;

-- 可选自检：
-- SELECT 'users' AS t, count(*) FROM users
-- UNION ALL SELECT 'couple_spaces', count(*) FROM couple_spaces
-- UNION ALL SELECT 'media_assets', count(*) FROM media_assets
-- UNION ALL SELECT 'schema_migrations', count(*) FROM schema_migrations;
