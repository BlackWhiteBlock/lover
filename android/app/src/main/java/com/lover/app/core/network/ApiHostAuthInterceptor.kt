package com.lover.app.core.network

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Adds Lover credentials only to the configured API origin. Any Authorization
 * header is stripped when a request is redirected or otherwise targets a
 * different origin.
 */
class ApiHostAuthInterceptor(
    private val apiBaseUrl: HttpUrl,
    private val accessToken: () -> String?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(secureRequest(chain.request()))

    fun secureRequest(request: Request): Request {
        val builder = request.newBuilder()
        if (!isApiHost(request.url)) {
            return builder.removeHeader("Authorization").build()
        }
        val token = accessToken()?.trim().orEmpty()
        if (token.isNotEmpty()) {
            // 始终写入最新 access token，避免并行请求仍带着旧 Bearer
            builder.header("Authorization", "Bearer $token")
        } else {
            builder.removeHeader("Authorization")
        }
        return builder.build()
    }

    fun isApiHost(url: HttpUrl): Boolean =
        url.scheme == apiBaseUrl.scheme &&
            url.host == apiBaseUrl.host &&
            url.port == apiBaseUrl.port
}
