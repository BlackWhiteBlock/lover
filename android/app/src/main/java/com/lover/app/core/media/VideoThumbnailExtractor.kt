package com.lover.app.core.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.graphics.scale
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class VideoThumbnail(val bytes: ByteArray, val fileName: String)

@Singleton
class VideoThumbnailExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun extract(uri: Uri, sourceName: String): VideoThumbnail {
        val retriever = MediaMetadataRetriever()
        val frame = try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            val candidates = buildList {
                if (durationMs > 0L) {
                    add((durationMs.coerceAtMost(1_000L)) * 1_000)
                    add((durationMs / 2) * 1_000)
                    add(0L)
                } else {
                    add(0L)
                    add(1_000_000L)
                }
            }
            candidates.firstNotNullOfOrNull { atUs ->
                retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } ?: retriever.frameAtTime
        } catch (error: RuntimeException) {
            throw IllegalArgumentException("无法从视频提取封面，请选择其他视频", error)
        } finally {
            runCatching { retriever.release() }
        } ?: throw IllegalArgumentException("无法从视频提取封面，请选择其他视频")

        val scaled = scaleDown(frame, 1280)
        val output = ByteArrayOutputStream()
        try {
            check(scaled.compress(Bitmap.CompressFormat.JPEG, 82, output)) { "视频封面压缩失败" }
        } finally {
            if (scaled !== frame) scaled.recycle()
            frame.recycle()
        }
        val baseName = sourceName.substringBeforeLast('.', sourceName)
        return VideoThumbnail(output.toByteArray(), "$baseName-thumbnail.jpg")
    }

    private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / largest
        return bitmap.scale(
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
        )
    }
}
