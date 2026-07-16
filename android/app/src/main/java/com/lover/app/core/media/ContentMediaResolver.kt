package com.lover.app.core.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
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

        val mimeType = resolveMimeType(uri, displayName)
            ?: throw IllegalArgumentException("无法识别媒体类型，请换一张照片或视频再试")
        val policy = MediaUploadPolicy.forMimeType(mimeType)

        // 华为等机型上 OpenableColumns.SIZE 对视频常不准确，优先用 AssetFileDescriptor
        val afdSize = resolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
            descriptor.length.takeIf { it > 0 }
                ?: descriptor.parcelFileDescriptor.statSize.takeIf { it > 0 }
        }
        val cursorSize = queriedSize?.takeIf { it > 0 }
        val size = when {
            afdSize != null && cursorSize != null && afdSize != cursorSize -> afdSize
            afdSize != null -> afdSize
            cursorSize != null -> cursorSize
            else -> throw IllegalArgumentException("无法可靠获取媒体文件大小")
        }
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

    private fun resolveMimeType(uri: Uri, displayName: String?): String? {
        val resolver = context.contentResolver
        resolver.getType(uri)?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
            ?.let { return normalizeMime(it) }

        val fromName = displayName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?.let { ext -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) }
        if (!fromName.isNullOrBlank()) return normalizeMime(fromName)

        val pathExt = uri.lastPathSegment
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?.let { ext -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) }
        return pathExt?.let(::normalizeMime)
    }

    private fun normalizeMime(mimeType: String): String = when (mimeType.lowercase()) {
        "image/jpg" -> "image/jpeg"
        "video/mp4v-es", "video/x-m4v" -> "video/mp4"
        else -> mimeType.lowercase()
    }

    private fun extension(mimeType: String) = when (mimeType) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/heic" -> "heic"
        "video/mp4" -> "mp4"
        "video/quicktime" -> "mov"
        "video/3gpp" -> "3gp"
        "video/3gpp2" -> "3g2"
        "video/webm" -> "webm"
        "video/x-matroska" -> "mkv"
        else -> "jpg"
    }
}
