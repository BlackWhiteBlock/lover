import type { FastifyReply, FastifyRequest } from 'fastify';
import type { Config } from './config.js';
import type { Database } from './db.js';

export interface AuthUser {
  id: string;
  phone: string;
  nickname: string;
  avatarUrl: string | null;
}

export interface AccessClaims {
  sub: string;
  sid: string;
  type: 'access';
}

export interface AppContext {
  config: Config;
  db: Database;
}

export type AuthHandler = (request: FastifyRequest, reply: FastifyReply) => Promise<void>;

declare module 'fastify' {
  interface FastifyRequest {
    user: AuthUser;
    sessionId: string;
  }
}
