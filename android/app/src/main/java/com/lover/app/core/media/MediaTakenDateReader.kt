package com.lover.app.core.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 从本地媒体 URI 尽量读取拍摄/创建日期，供时光「记录日期」预填。
 * 读不到时返回 null，由调用方保留默认日期。
 */
object MediaTakenDateReader {
    private val exifDateTime =
        DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US)
    private val mediaMetadataDate =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSSXX", Locale.US)

    fun readLocalDate(context: Context, uri: Uri): LocalDate? {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val taken = readFromExif(context, uri)
            ?: readFromVideoMetadata(context, uri)
            ?: readFromMediaStore(context, uri)
            ?: return null
        return minOf(taken, today)
    }

    private fun readFromExif(context: Context, uri: Uri): LocalDate? {
        readExifFromDescriptor(context, uri)?.let { return it }
        return readExifFromStream(context, uri)
    }

    private fun readExifFromDescriptor(context: Context, uri: Uri): LocalDate? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            parseExifDate(ExifInterface(pfd.fileDescriptor))
        }
    }.getOrNull()

    private fun readExifFromStream(context: Context, uri: Uri): LocalDate? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            parseExifDate(ExifInterface(stream))
        }
    }.getOrNull()

    private fun parseExifDate(exif: ExifInterface): LocalDate? {
        val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
            ?: return null
        return LocalDate.parse(raw.take(19), exifDateTime)
    }

    private fun readFromVideoMetadata(context: Context, uri: Uri): LocalDate? {
        val mime = context.contentResolver.getType(uri).orEmpty()
        if (!mime.startsWith("video/")) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                ?: return null
            parseVideoMetadataDate(raw)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun parseVideoMetadataDate(raw: String): LocalDate? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("1904")) return null
        runCatching { LocalDate.parse(trimmed, mediaMetadataDate) }.getOrNull()?.let { return it }
        // 部分设备返回 yyyyMMdd'T'HHmmss 无时区
        runCatching {
            LocalDate.parse(trimmed.take(15), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.US))
        }.getOrNull()?.let { return it }
        return null
    }

    private fun readFromMediaStore(context: Context, uri: Uri): LocalDate? = runCatching {
        val projection = arrayOf(
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val zone = ZoneId.systemDefault()
            val takenIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
            if (takenIndex >= 0 && !cursor.isNull(takenIndex)) {
                val millis = cursor.getLong(takenIndex)
                if (millis > 0L) {
                    return@use Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
                }
            }
            for (column in listOf(
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.DATE_MODIFIED,
            )) {
                val index = cursor.getColumnIndex(column)
                if (index >= 0 && !cursor.isNull(index)) {
                    val seconds = cursor.getLong(index)
                    if (seconds > 0L) {
                        return@use Instant.ofEpochSecond(seconds).atZone(zone).toLocalDate()
                    }
                }
            }
            null
        }
    }.getOrNull()
}
