/**
 * HTTP client wrapper using @ohos.net.http.
 * Provides token injection and automatic 401 refresh (with retry cap).
 */
import http from '@ohos.net.http'
import { Constants } from '../../common/Constants'
import { BackendException } from './NetworkErrors'
import type { TokenResponse, JsonObject } from '../model/Models'

export type TokenProvider = () => string | null
export type TokenRefresher = (refreshToken: string) => Promise<TokenResponse>
export type TokenSaver = (accessToken: string, refreshToken: string) => void
export type SessionClearer = () => void

const MAX_AUTH_RETRY = 1

export class HttpClient {
  private tokenProvider: TokenProvider
  private getRefreshToken: () => string | null = () => null
  private tokenRefresher: TokenRefresher | null = null
  private tokenSaver: TokenSaver | null = null
  private sessionClearer: SessionClearer | null = null
  private refreshing: boolean = false
  private refreshQueue: Array<(token: string | null) => void> = []

  constructor(tokenProvider: TokenProvider, getRefreshToken: () => string | null) {
    this.tokenProvider = tokenProvider
    this.getRefreshToken = getRefreshToken
  }

  setRefreshHandlers(
    refresher: TokenRefresher,
    saver: TokenSaver,
    clearer: SessionClearer,
  ): void {
    this.tokenRefresher = refresher
    this.tokenSaver = saver
    this.sessionClearer = clearer
  }

  private buildAuthHeaders(
    extra?: Record<string, string>,
    withJsonContentType: boolean = true,
  ): Record<string, string> {
    const headers: Record<string, string> = {}
    if (withJsonContentType) {
      headers['Content-Type'] = 'application/json'
    }
    headers['Accept'] = 'application/json, text/plain, */*'
    const token = this.tokenProvider()
    if (token && token.length > 0) {
      headers['Authorization'] = `Bearer ${token}`
    }
    if (extra) {
      const keys = Object.keys(extra)
      for (const k of keys) {
        headers[k] = extra[k]
      }
    }
    return headers
  }

  private encodeQuery(queryParams: Map<string, string>): string {
    const parts: string[] = []
    queryParams.forEach((value: string, key: string) => {
      parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    })
    return parts.join('&')
  }

  private async doRequest(
    url: string,
    method: http.RequestMethod,
    body?: string,
    extraHeaders?: Record<string, string>,
    authRetry: number = 0,
    withJsonContentType: boolean = true,
  ): Promise<JsonObject> {
    const fullUrl = url.startsWith('http') ? url : `${Constants.API_BASE_URL}${url}`
    const headers = this.buildAuthHeaders(extraHeaders, withJsonContentType)
    try {
      const httpRequest = http.createHttp()
      const options: http.HttpRequestOptions = {
        method: method,
        header: headers,
        connectTimeout: 30000,
        readTimeout: 30000,
      }
      if (body !== undefined) {
        options.extraData = body
      }
      const response = await httpRequest.request(fullUrl, options)
      httpRequest.destroy()

      const respData = response.result as string
      if (response.responseCode === 401) {
        if (authRetry >= MAX_AUTH_RETRY) {
          this.sessionClearer?.()
          throw new BackendException('UNAUTHORIZED', '会话已失效，请重新登录')
        }
        const newToken = await this.tryRefresh()
        if (newToken) {
          return this.doRequest(url, method, body, extraHeaders, authRetry + 1, withJsonContentType)
        }
        this.sessionClearer?.()
        throw new BackendException('UNAUTHORIZED', '会话已失效，请重新登录')
      }
      if (response.responseCode < 200 || response.responseCode >= 300) {
        throw this.parseError(respData, response.responseCode)
      }
      if (!respData || respData.length === 0) {
        return {} as JsonObject
      }
      return JSON.parse(respData) as JsonObject
    } catch (e) {
      if (e instanceof BackendException) throw e
      throw new BackendException('NETWORK_ERROR', '无法连接服务器，请检查网络')
    }
  }

  private parseError(respData: string, statusCode: number): BackendException {
    try {
      const parsed = JSON.parse(respData) as JsonObject
      const apiError = parsed.error as JsonObject
      if (apiError) {
        return new BackendException(
          (apiError.code as string) ?? `HTTP_${statusCode}`,
          (apiError.message as string) ?? `服务器请求失败（${statusCode}）`
        )
      }
    } catch (_e) {
      // ignore parse error
    }
    return new BackendException(`HTTP_${statusCode}`, `服务器请求失败（${statusCode}）`)
  }

  private async tryRefresh(): Promise<string | null> {
    const refreshToken = this.getRefreshToken()
    if (!refreshToken) return null
    if (!this.tokenRefresher || !this.tokenSaver) return null

    if (this.refreshing) {
      return new Promise<string | null>((resolve) => {
        this.refreshQueue.push(resolve)
      })
    }

    this.refreshing = true
    try {
      const body: JsonObject = {}
      body.refreshToken = refreshToken
      const bodyStr = JSON.stringify(body)

      const httpRequest = http.createHttp()
      const refreshHeaders: Record<string, string> = {}
      refreshHeaders['Content-Type'] = 'application/json'
      const response = await httpRequest.request(`${Constants.API_BASE_URL}api/auth/refresh`, {
        method: http.RequestMethod.POST,
        header: refreshHeaders as Object,
        extraData: bodyStr,
        connectTimeout: 30000,
        readTimeout: 30000,
      })
      httpRequest.destroy()

      if (response.responseCode < 200 || response.responseCode >= 300) {
        this.refreshQueue.forEach((cb) => cb(null))
        this.refreshQueue = []
        return null
      }

      const respData = response.result as string
      if (!respData || respData.length === 0) {
        this.refreshQueue.forEach((cb) => cb(null))
        this.refreshQueue = []
        return null
      }
      const data = JSON.parse(respData) as JsonObject
      const accessToken = (data.accessToken as string) ?? ''
      const newRefreshToken = (data.refreshToken as string) ?? ''
      if (!accessToken) {
        this.refreshQueue.forEach((cb) => cb(null))
        this.refreshQueue = []
        return null
      }
      this.tokenSaver(accessToken, newRefreshToken)
      this.refreshQueue.forEach((cb) => cb(accessToken))
      this.refreshQueue = []
      return accessToken
    } catch (_e) {
      this.refreshQueue.forEach((cb) => cb(null))
      this.refreshQueue = []
      return null
    } finally {
      this.refreshing = false
    }
  }

  async get<T>(url: string, queryParams?: Map<string, string>): Promise<T> {
    let finalUrl = url
    if (queryParams && queryParams.size > 0) {
      finalUrl = `${url}?${this.encodeQuery(queryParams)}`
    }
    const result = await this.doRequest(finalUrl, http.RequestMethod.GET)
    return result as T
  }

  async post<T>(url: string, data?: Object): Promise<T> {
    const body = data ? JSON.stringify(data) : undefined
    const result = await this.doRequest(url, http.RequestMethod.POST, body)
    return result as T
  }

  async patch<T>(url: string, data?: Object): Promise<T> {
    const body = data ? JSON.stringify(data) : undefined
    const result = await this.doRequest(url, http.RequestMethod.PATCH, body)
    return result as T
  }

  async put<T>(url: string, data?: Object): Promise<T> {
    const body = data ? JSON.stringify(data) : undefined
    const result = await this.doRequest(url, http.RequestMethod.PUT, body)
    return result as T
  }

  async delete<T>(url: string): Promise<T> {
    // Harmony @ohos.net.http often still attaches Content-Type: application/json on DELETE.
    // Fastify then throws FST_ERR_CTP_EMPTY_JSON_BODY when the body is empty.
    // Send a no-op JSON object (backend DELETE handlers ignore the body).
    const result = await this.doRequest(
      url,
      http.RequestMethod.DELETE,
      '{}',
      undefined,
      0,
      true,
    )
    return result as T
  }
}
