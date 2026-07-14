import pg, { type PoolClient, type QueryResultRow } from 'pg';
import type { Config } from './config.js';

const { Pool, types } = pg;
types.setTypeParser(1082, (value: string) => value);

export type Database = ReturnType<typeof createDatabase>;

export function createDatabase(config: Config) {
  const pool = new Pool({ connectionString: config.databaseUrl });
  return {
    pool,
    query<T extends QueryResultRow = QueryResultRow>(text: string, values: unknown[] = []) {
      return pool.query<T>(text, values);
    },
    async transaction<T>(work: (client: PoolClient) => Promise<T>) {
      const client = await pool.connect();
      try {
        await client.query('begin');
        const result = await work(client);
        await client.query('commit');
        return result;
      } catch (error) {
        await client.query('rollback');
        throw error;
      } finally {
        client.release();
      }
    },
    close() {
      return pool.end();
    },
  };
}
