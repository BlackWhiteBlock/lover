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
        if (responseCount(response) >= 2) return null
        val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ") ?: return null
        if (failedToken != tokenStore.snapshot.accessToken) return null

        return synchronized(refreshLock) {
            val current = tokenStore.snapshot
            if (current.accessToken != null && current.accessToken != failedToken) {
                return@synchronized retry(response.request, current.accessToken)
            }
            val refreshToken = current.refreshToken ?: return@synchronized null
            runBlocking {
                runCatching { refreshApi.refresh(RefreshRequest(refreshToken)) }
                    .onSuccess { tokenStore.saveTokens(it.accessToken, it.refreshToken) }
                    .onFailure { tokenStore.clearSession() }
                    .getOrNull()
                    ?.let { retry(response.request, it.accessToken) }
            }
        }
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
