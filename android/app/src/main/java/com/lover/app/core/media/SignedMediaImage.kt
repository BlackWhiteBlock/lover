package com.lover.app.core.media

import android.content.Context
import coil.request.ImageRequest

private val ephemeralQueryParam =
    Regex("""([?&])(?:token|e|sign|attname|expires|Expires|Signature)=[^&]*""", RegexOption.IGNORE_CASE)

/**
 * 私有媒体签名 URL 每次都会变（token / e），若直接用完整 URL 作缓存键，
 * Coil 会反复下载同一张图。去掉易变签名参数，保留路径与七牛图片处理参数。
 */
fun stableSignedMediaCacheKey(url: String): String {
    var cleaned = ephemeralQueryParam.replace(url, "$1")
    cleaned = cleaned.replace("?&", "?")
    cleaned = cleaned.replace("&&", "&")
    if (cleaned.endsWith('?') || cleaned.endsWith('&')) {
        cleaned = cleaned.dropLast(1)
    }
    return cleaned.ifBlank { url }
}

fun signedMediaImageRequest(
    context: Context,
    url: String,
    cacheKey: String? = null,
    crossfade: Boolean = true,
): ImageRequest {
    val key = cacheKey?.takeIf { it.isNotBlank() } ?: stableSignedMediaCacheKey(url)
    return ImageRequest.Builder(context)
        .data(url)
        .memoryCacheKey(key)
        .diskCacheKey(key)
        .crossfade(crossfade)
        .listener(
            onError = { request, result ->
                MediaLoadDiagnostics.onCoilError(request, result)
            },
        )
        .build()
}

/** 时光列表/缩略图：稳定缓存键 + 无 crossfade，避免刷新时整页闪白 */
fun listMediaImageRequest(
    context: Context,
    url: String,
    assetId: String,
): ImageRequest = signedMediaImageRequest(
    context = context,
    url = url,
    cacheKey = "media-thumb-$assetId",
    crossfade = false,
)
