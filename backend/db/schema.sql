-- Lover PostgreSQL schema. The executable, versioned definition is:
-- db/migrations/001_initial.sql
--
-- Fresh database:
--   npm run db:migrate
--
-- This schema manifest intentionally points at the migration as the single
-- source of truth so schema and migration cannot silently diverge.
\ir migrations/001_initial.sql
