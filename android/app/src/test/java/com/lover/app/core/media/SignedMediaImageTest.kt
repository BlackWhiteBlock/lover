package com.lover.app.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignedMediaImageTest {
    @Test
    fun `strips qiniu private token params but keeps image fop`() {
        val signed = "https://cdn.example.com/couples/a/b.jpg" +
            "?imageMogr2/auto-orient/thumbnail/800x/format/webp/quality/75" +
            "&e=1710000000&token=abc123"
        val key = stableSignedMediaCacheKey(signed)
        assertTrue(key.contains("imageMogr2/auto-orient/thumbnail/800x/format/webp/quality/75"))
        assertFalse(key.contains("token="))
        assertFalse(key.contains("e=1710000000"))
        val resigned = "https://cdn.example.com/couples/a/b.jpg" +
            "?imageMogr2/auto-orient/thumbnail/800x/format/webp/quality/75" +
            "&e=1710003600&token=xyz999"
        assertEquals(key, stableSignedMediaCacheKey(resigned))
    }

    @Test
    fun `strips local private-media token`() {
        val a = "https://api.example.com/private-media/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa?token=old"
        val b = "https://api.example.com/private-media/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa?token=new"
        assertEquals(stableSignedMediaCacheKey(a), stableSignedMediaCacheKey(b))
        assertFalse(stableSignedMediaCacheKey(a).contains("token="))
    }
}
