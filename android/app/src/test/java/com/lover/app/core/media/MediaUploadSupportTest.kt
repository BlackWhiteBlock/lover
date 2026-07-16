package com.lover.app.core.media

import java.io.ByteArrayInputStream
import okhttp3.MediaType.Companion.toMediaType
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaUploadSupportTest {
    @Test
    fun `streaming body opens source only while writing`() {
        val content = "streamed-media".encodeToByteArray()
        var opens = 0
        val body = StreamingRequestBody("video/mp4".toMediaType(), content.size.toLong()) {
            opens++
            ByteArrayInputStream(content)
        }

        assertEquals(0, opens)
        assertEquals(content.size.toLong(), body.contentLength())
        val sink = Buffer()
        body.writeTo(sink)

        assertEquals(1, opens)
        assertEquals("streamed-media", sink.readUtf8())
    }

    @Test
    fun `media policy distinguishes image and video limits`() {
        val image = MediaUploadPolicy.forMimeType("image/jpeg")
        val video = MediaUploadPolicy.forMimeType("video/mp4")
        val huaweiVideo = MediaUploadPolicy.forMimeType("video/3gpp")

        assertFalse(image.isVideo)
        assertEquals(30L * 1024 * 1024, image.maxBytes)
        assertTrue(video.isVideo)
        assertTrue(huaweiVideo.isVideo)
        assertEquals(200L * 1024 * 1024, video.maxBytes)
    }

    @Test
    fun `media policy rejects unsupported picker content`() {
        assertThrows(IllegalArgumentException::class.java) {
            MediaUploadPolicy.forMimeType("application/octet-stream")
        }
    }
}
