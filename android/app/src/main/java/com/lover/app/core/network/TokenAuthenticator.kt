package com.lover.app.core.network

import com.lover.app.core.data.TokenStore
import com.lover.app.core.model.RefreshRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    private val refreshApi: RefreshApi,
) : Authenticator {
    private val refreshLock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) {
            invalidateSession()
            return null
        }
        val failedToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (failedToken == null) {
            invalidateSession()
            return null
        }

        return synchronized(refreshLock) {
            val current = tokenStore.snapshot
            val latestAccess = current.accessToken
            // 其他请求已完成刷新：直接用新 access token 重试
            if (!latestAccess.isNullOrBlank() && latestAccess != failedToken) {
                return@synchronized retry(response.request, latestAccess)
            }

            val refreshToken = current.refreshToken
            if (refreshToken.isNullOrBlank()) {
                invalidateSession()
                return@synchronized null
            }

            runBlocking {
                val refreshed = runCatching {
                    refreshApi.refresh(RefreshRequest(refreshToken))
                }.getOrNull()

                if (refreshed != null) {
                    tokenStore.saveTokens(refreshed.accessToken, refreshed.refreshToken)
                    retry(response.request, refreshed.accessToken)
                } else {
                    // 刷新失败：清空会话，UI 统一回登录页
                    if (tokenStore.snapshot.refreshToken == refreshToken ||
                        tokenStore.snapshot.accessToken == failedToken
                    ) {
                        tokenStore.clearSession()
                    }
                    null
                }
            }
        }
    }

    private fun invalidateSession() {
        runBlocking { tokenStore.clearSession() }
    }

    private fun retry(request: Request, token: String): Request =
        request.newBuilder().header("Authorization", "Bearer $token").build()

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
