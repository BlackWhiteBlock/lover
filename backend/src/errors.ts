import type { FastifyReply, FastifyRequest } from 'fastify';
import { ZodError } from 'zod';

export class AppError extends Error {
  constructor(
    public readonly statusCode: number,
    public readonly code: string,
    message: string,
    public readonly details?: unknown,
  ) {
    super(message);
  }
}

export const badRequest = (code: string, message: string, details?: unknown) =>
  new AppError(400, code, message, details);
export const unauthorized = (message = '请先登录') =>
  new AppError(401, 'UNAUTHORIZED', message);
export const forbidden = (message = '无权访问该资源') =>
  new AppError(403, 'FORBIDDEN', message);
export const notFound = (message = '资源不存在') =>
  new AppError(404, 'NOT_FOUND', message);
export const conflict = (code: string, message: string) =>
  new AppError(409, code, message);

export function errorHandler(error: unknown, request: FastifyRequest, reply: FastifyReply) {
  if (error instanceof ZodError) {
    return reply.code(400).send({
      error: {
        code: 'VALIDATION_ERROR',
        message: '请求参数无效',
        details: error.issues.map(({ path, message, code }) => ({ path, message, code })),
      },
    });
  }
  if (error instanceof AppError) {
    return reply.code(error.statusCode).send({
      error: { code: error.code, message: error.message, details: error.details },
    });
  }
  const databaseCode = typeof error === 'object' && error !== null && 'code' in error
    ? String(error.code)
    : '';
  if (databaseCode === '23505' || databaseCode === '23514') {
    return reply.code(409).send({
      error: { code: 'DATA_CONFLICT', message: '操作与当前数据状态冲突，请刷新后重试' },
    });
  }
  request.log.error({ err: error }, 'unhandled request error');
  return reply.code(500).send({
    error: { code: 'INTERNAL_ERROR', message: '服务器内部错误' },
  });
}
