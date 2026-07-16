package com.lover.app.core.media

import java.io.InputStream
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

class StreamingRequestBody(
    private val mediaType: MediaType,
    private val length: Long,
    private val openStream: () -> InputStream,
) : RequestBody() {
    init {
        require(length > 0) { "媒体大小必须大于 0" }
    }

    override fun contentType(): MediaType = mediaType
    override fun contentLength(): Long = length

    override fun writeTo(sink: BufferedSink) {
        openStream().use { input ->
            input.source().use { source -> sink.writeAll(source) }
        }
    }
}

data class MediaUploadPolicy(
    val isVideo: Boolean,
    val maxBytes: Long,
) {
    companion object {
        const val IMAGE_MAX_BYTES = 30L * 1024 * 1024
        const val VIDEO_MAX_BYTES = 200L * 1024 * 1024

        private val imageMime = setOf("image/jpeg", "image/png", "image/webp", "image/heic")
        private val videoMime = setOf(
            "video/mp4",
            "video/quicktime",
            "video/3gpp",
            "video/3gpp2",
            "video/webm",
            "video/x-matroska",
        )

        fun forMimeType(mimeType: String): MediaUploadPolicy = when {
            mimeType in imageMime ->
                MediaUploadPolicy(isVideo = false, maxBytes = IMAGE_MAX_BYTES)
            mimeType in videoMime ->
                MediaUploadPolicy(isVideo = true, maxBytes = VIDEO_MAX_BYTES)
            else -> throw IllegalArgumentException("不支持的媒体类型：$mimeType")
        }
    }
}
