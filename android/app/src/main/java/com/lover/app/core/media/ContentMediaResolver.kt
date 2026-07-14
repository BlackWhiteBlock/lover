package com.lover.app.core.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType

data class ResolvedMedia(
    val uri: Uri,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val isVideo: Boolean,
    val body: StreamingRequestBody,
)

@Singleton
class ContentMediaResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun resolve(uri: Uri): ResolvedMedia {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: throw IllegalArgumentException("无法识别媒体类型")
        val policy = MediaUploadPolicy.forMimeType(mimeType)
        var displayName: String? = null
        var queriedSize: Long? = null
        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) displayName = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) queriedSize = cursor.getLong(sizeIndex)
            }
        }
        val size = queriedSize?.takeIf { it > 0 }
            ?: resolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.length.takeIf { it > 0 }
                    ?: descriptor.parcelFileDescriptor.statSize.takeIf { it > 0 }
            }
            ?: throw IllegalArgumentException("无法可靠获取媒体文件大小")
        require(size <= policy.maxBytes) {
            if (policy.isVideo) "视频不能超过 200 MB" else "图片不能超过 30 MB"
        }
        val fileName = displayName?.takeIf { it.isNotBlank() }
            ?: "lover-${System.currentTimeMillis()}.${extension(mimeType)}"
        val body = StreamingRequestBody(mimeType.toMediaType(), size) {
            resolver.openInputStream(uri) ?: throw IOException("无法读取所选媒体")
        }
        return ResolvedMedia(uri, fileName, mimeType, size, policy.isVideo, body)
    }

    private fun extension(mimeType: String) = when (mimeType) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/heic" -> "heic"
        "video/mp4" -> "mp4"
        "video/quicktime" -> "mov"
        else -> "jpg"
    }
}
