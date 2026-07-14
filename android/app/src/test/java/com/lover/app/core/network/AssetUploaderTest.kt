package com.lover.app.core.network

import com.lover.app.core.model.TokenAssetResponse
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetUploaderTest {
    private val uploader = AssetUploader(OkHttpClient(), Json)
    private val bytes = "image-bytes".encodeToByteArray()

    @Test
    fun `local upload uses only storage bearer token`() {
        val request = uploader.buildRequest(
            grant(provider = "local", uploadToken = "local-storage-token"),
            "photo.jpg",
            "image/jpeg",
            bytes,
        )

        assertEquals("Bearer local-storage-token", request.header("Authorization"))
        assertTrue(body(request).contains("name=\"file\"; filename=\"photo.jpg\""))
        assertTrue(!body(request).contains("lover-access-token"))
    }

    @Test
    fun `qiniu upload has fields and no authorization header`() {
        val request = uploader.buildRequest(
            grant(
                provider = "qiniu",
                uploadToken = "qiniu-upload-token",
                uploadFields = mapOf("key" to "couples/space/photo.jpg", "x:trace" to "trace-value"),
            ),
            "photo.jpg",
            "image/jpeg",
            bytes,
        )
        val body = body(request)

        assertNull(request.header("Authorization"))
        assertTrue(body.contains("name=\"token\""))
        assertTrue(body.contains("qiniu-upload-token"))
        assertTrue(body.contains("name=\"key\""))
        assertTrue(body.contains("couples/space/photo.jpg"))
        assertTrue(body.contains("name=\"x:trace\""))
        assertTrue(body.contains("name=\"file\"; filename=\"photo.jpg\""))
    }

    @Test
    fun `unknown provider is rejected before network request`() {
        assertThrows(BackendException::class.java) {
            uploader.buildRequest(grant(provider = "unknown"), "photo.jpg", "image/jpeg", bytes)
        }
    }

    private fun grant(
        provider: String,
        uploadToken: String = "upload-token",
        uploadFields: Map<String, String> = emptyMap(),
    ) = TokenAssetResponse(
        assetId = "asset-id",
        provider = provider,
        uploadToken = uploadToken,
        uploadUrl = "https://upload.example.test/",
        objectKey = "couples/space/photo.jpg",
        uploadFields = uploadFields,
        expiresIn = 600,
    )

    private fun body(request: okhttp3.Request): String =
        Buffer().also { request.body!!.writeTo(it) }.readUtf8()
}
