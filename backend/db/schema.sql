-- Lover PostgreSQL schema. The executable, versioned definition is:
-- db/migrations/*.sql
--
-- Fresh database:
--   npm run db:migrate
--
-- This schema manifest intentionally points at the migration as the single
-- source of truth so schema and migration cannot silently diverge.
\ir migrations/001_initial.sql
\ir migrations/002_qiniu_storage.sql
\ir migrations/003_media_item_assets.sql
