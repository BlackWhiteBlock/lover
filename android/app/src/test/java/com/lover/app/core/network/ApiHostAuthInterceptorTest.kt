package com.lover.app.core.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiHostAuthInterceptorTest {
    private val interceptor = ApiHostAuthInterceptor(
        "https://api.lover.example/api/".toHttpUrl(),
    ) { "lover-access-token" }

    @Test
    fun `api origin receives bearer token`() {
        val secured = interceptor.secureRequest(
            Request.Builder().url("https://api.lover.example/api/me").build(),
        )

        assertEquals("Bearer lover-access-token", secured.header("Authorization"))
        assertTrue(interceptor.isApiHost(secured.url))
    }

    @Test
    fun `stale authorization header is replaced with latest access token`() {
        val secured = interceptor.secureRequest(
            Request.Builder()
                .url("https://api.lover.example/api/me")
                .header("Authorization", "Bearer expired-token")
                .build(),
        )

        assertEquals("Bearer lover-access-token", secured.header("Authorization"))
    }

    @Test
    fun `third party upload never receives authorization`() {
        val external = Request.Builder()
            .url("https://upload.qiniup.com/")
            .header("Authorization", "Bearer lover-access-token")
            .build()

        val secured = interceptor.secureRequest(external)

        assertNull(secured.header("Authorization"))
        assertFalse(interceptor.isApiHost(secured.url))
    }

    @Test
    fun `same hostname on a different port is not API origin`() {
        val request = Request.Builder().url("https://api.lover.example:444/upload").build()
        assertFalse(interceptor.isApiHost(request.url))
    }
}
