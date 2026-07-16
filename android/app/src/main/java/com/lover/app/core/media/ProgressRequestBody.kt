package com.lover.app.core.media

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer

/** 包装上传体，回调已写入字节数，用于 UI 进度。 */
class ProgressRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (bytesWritten: Long, contentLength: Long) -> Unit,
) : RequestBody() {
    override fun contentType(): MediaType? = delegate.contentType()
    override fun contentLength(): Long = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val contentLength = contentLength().coerceAtLeast(0L)
        var lastEmitted = -1L
        val counting = object : ForwardingSink(sink) {
            var written = 0L
            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                written += byteCount
                // 降频：约每 2% 或每 256KB 回调一次
                val step = if (contentLength > 0) {
                    (contentLength / 50).coerceAtLeast(256 * 1024L)
                } else {
                    256 * 1024L
                }
                if (written == contentLength || written - lastEmitted >= step) {
                    lastEmitted = written
                    onProgress(written, contentLength)
                }
            }
        }
        val buffered = counting.buffer()
        delegate.writeTo(buffered)
        buffered.flush()
        if (contentLength > 0) onProgress(contentLength, contentLength)
    }
}
