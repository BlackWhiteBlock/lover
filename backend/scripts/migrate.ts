import fs from 'node:fs/promises';
import path from 'node:path';
import dotenv from 'dotenv';
import pg from 'pg';
import { orderedMigrationNames } from '../src/migrations.js';

dotenv.config({ override: true });

const databaseUrl = process.env.DATABASE_URL ?? 'postgres://postgres:postgres@localhost:5432/lover';
const migrationsDir = path.resolve(process.cwd(), 'db/migrations');
const pool = new pg.Pool({ connectionString: databaseUrl });

try {
  await pool.query(`
    create table if not exists schema_migrations (
      name text primary key,
      applied_at timestamptz not null default now()
    )
  `);
  const files = orderedMigrationNames(await fs.readdir(migrationsDir));
  for (const name of files) {
    const applied = await pool.query(`select 1 from schema_migrations where name = $1`, [name]);
    if (applied.rowCount) continue;
    const sql = await fs.readFile(path.join(migrationsDir, name), 'utf8');
    const client = await pool.connect();
    try {
      await client.query('begin');
      await client.query(`select pg_advisory_xact_lock(hashtext('lover-schema-migrations'))`);
      const raced = await client.query(`select 1 from schema_migrations where name = $1`, [name]);
      if (!raced.rowCount) {
        await client.query(sql);
        await client.query(`insert into schema_migrations(name) values ($1)`, [name]);
        console.info(`Applied ${name}`);
      }
      await client.query('commit');
    } catch (error) {
      await client.query('rollback');
      throw error;
    } finally {
      client.release();
    }
  }
} finally {
  await pool.end();
}
