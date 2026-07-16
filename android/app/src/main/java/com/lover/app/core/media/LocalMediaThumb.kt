package com.lover.app.core.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lover.app.core.design.Peach
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun isLocalVideoUri(context: Context, uri: Uri): Boolean {
    val mime = context.contentResolver.getType(uri)
    if (mime?.startsWith("video/") == true) return true
    if (mime?.startsWith("image/") == true) return false
    val name = uri.lastPathSegment?.lowercase().orEmpty()
    return listOf(".mp4", ".mov", ".m4v", ".mkv", ".3gp", ".webm", ".avi").any { name.endsWith(it) }
}

/** 本地视频封面：优先系统缩略图，失败再抽帧。 */
fun loadLocalVideoThumbnail(context: Context, uri: Uri, maxSize: Int = 512): Bitmap? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching {
            context.contentResolver.loadThumbnail(uri, Size(maxSize, maxSize), null)
        }.getOrNull()?.let { return it }
    }
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?: 0L
        val atUs = when {
            durationMs <= 0L -> 0L
            durationMs < 2_000L -> 0L
            else -> 1_000_000L
        }
        retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.frameAtTime
    } catch (_: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

@Composable
fun LocalMediaThumb(
    uri: Uri,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    showVideoBadge: Boolean = true,
) {
    val context = LocalContext.current
    val isVideo = isLocalVideoUri(context, uri)
    if (!isVideo) {
        AsyncImage(
            model = uri,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
        return
    }

    val thumb by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { loadLocalVideoThumbnail(context, uri) }.getOrNull()
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (thumb != null) {
            AsyncImage(
                model = thumb,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Peach.copy(alpha = 0.45f)),
            )
        }
        if (showVideoBadge) {
            Icon(
                Icons.Rounded.PlayCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
