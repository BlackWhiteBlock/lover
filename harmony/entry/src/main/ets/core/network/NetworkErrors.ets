/**
 * Network error types and utilities.
 * Mirror of Android's NetworkErrors.kt
 */
import { Constants } from '../../common/Constants'

export class BackendException extends Error {
  code: string

  constructor(code: string, message: string) {
    super(message)
    this.code = code
    this.name = 'BackendException'
  }
}

export function isUnauthorized(error: Error): boolean {
  if (error instanceof BackendException) {
    if (error.code === 'UNAUTHORIZED' || error.code === 'HTTP_401') return true
  }
  const msg = error.message ?? ''
  return msg.includes('令牌') || msg.includes('过期') ||
    msg.includes('请先登录') || msg.includes('会话已失效')
}

export function toUserFacing(error: Error): BackendException {
  if (error instanceof BackendException) return error
  return new BackendException('NETWORK_ERROR', `无法连接服务器${Constants.DEBUG ? '\n' + Constants.API_BASE_URL : '，请检查网络与后端地址'}`)
}
