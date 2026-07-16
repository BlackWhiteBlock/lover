@file:OptIn(ExperimentalMaterial3Api::class)

package com.lover.app.feature.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.lover.app.core.design.Blush
import com.lover.app.core.design.Rose
import com.lover.app.core.design.Stone
import com.lover.app.core.design.WarmBackground
import com.lover.app.core.media.listMediaImageRequest
import com.lover.app.core.model.CoupleMember
import com.lover.app.core.model.MediaAssetPart
import com.lover.app.core.model.MediaItem
import com.lover.app.core.model.MediaType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CreamWhite = WarmBackground
private val SoftRoseBorder = Color(0xFFFFF1F2)
private val SoftRoseFill = Color(0xFFFFF1F2)
private val LightRoseLine = Color(0xFFFECDD3)

@Composable
fun MediaDetailScreen(
    item: MediaItem,
    members: List<CoupleMember>,
    onClose: () -> Unit,
    onEdit: () -> Unit,
) {
    BackHandler(onBack = onClose)
    var lightboxIndex by remember { mutableStateOf<Int?>(null) }
    val assets = item.assets.sortedBy { it.sortOrder }
    val cover = assets.firstOrNull()
    val coverUrl = cover?.previewUrl.orEmpty()
    val displayDate = formatMemoryDate(item.mediaDate)
    val title = item.caption.trim().lineSequence().firstOrNull()?.takeIf { it.isNotBlank() }
        ?: "我们的时光"
    val mood = when {
        assets.isEmpty() -> "瞬间"
        assets.size > 1 -> "相册"
        assets.first().type == MediaType.VIDEO -> "视频"
        else -> "照片"
    }
    val uploaderName = members.firstOrNull { it.id == item.uploaderId }?.nickname
        ?: if (item.uploaderId.isNullOrBlank()) "我们" else "TA"

    val configuration = LocalConfiguration.current
    val heroHeight = (configuration.screenHeightDp * 0.62f).dp

    Box(
        Modifier
            .fillMaxSize()
            .background(CreamWhite),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // Hero 62vh
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(heroHeight),
            ) {
                if (coverUrl.isNotBlank() && cover != null) {
                    val context = LocalContext.current
                    AsyncImage(
                        model = listMediaImageRequest(context, coverUrl, cover.assetId),
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(Blush))
                }
                // 顶部保护 + 底部压暗
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.35f),
                                0.35f to Color.Transparent,
                                0.7f to Color.Black.copy(alpha = 0.25f),
                                1f to Color.Black.copy(alpha = 0.5f),
                            ),
                        ),
                )
                // 底部融入奶油白
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.55f to Color.Transparent,
                                1f to CreamWhite,
                            ),
                        ),
                )

                // 玻璃导航
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassIconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    GlassIconButton(onClick = onEdit) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = "编辑",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                // 心情 + 标题
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 28.dp),
                ) {
                    Text(
                        mood.uppercase(Locale.getDefault()),
                        color = Color.White,
                        fontSize = 9.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color = Color.White,
                            fontSize = 30.sp,
                            lineHeight = 36.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // 内容区
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 40.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            tint = Stone.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            displayDate,
                            color = Stone.copy(alpha = 0.75f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Light,
                        )
                    }
                    Text(
                        uploaderName,
                        color = Stone.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Light,
                    )
                }

                if (item.caption.isNotBlank()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(32.dp))
                            .background(Color.White.copy(alpha = 0.72f))
                            .border(1.dp, SoftRoseBorder, RoundedCornerShape(32.dp))
                            .padding(24.dp),
                    ) {
                        Text(
                            item.caption,
                            color = Stone.copy(alpha = 0.9f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Light,
                            lineHeight = 26.sp,
                        )
                        Spacer(Modifier.height(20.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .width(16.dp)
                                    .height(1.dp)
                                    .background(LightRoseLine),
                            )
                            Text(
                                "只有我们知道",
                                color = Stone.copy(alpha = 0.35f),
                                fontSize = 9.sp,
                                letterSpacing = 3.sp,
                                fontWeight = FontWeight.Light,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(1.dp)
                                    .background(SoftRoseFill.copy(alpha = 0.7f)),
                            )
                        }
                    }
                }

                if (assets.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "${assets.size} 个瞬间",
                            color = Stone.copy(alpha = 0.35f),
                            fontSize = 9.sp,
                            letterSpacing = 3.sp,
                            fontWeight = FontWeight.Light,
                        )

                        val hero = assets.first()
                        MemoryMediaTile(
                            asset = hero,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3f)
                                .clip(RoundedCornerShape(32.dp)),
                            videoSize = 56.dp,
                            onClick = { lightboxIndex = 0 },
                        )

                        val pair = assets.drop(1).take(2)
                        if (pair.isNotEmpty()) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                pair.forEachIndexed { i, asset ->
                                    MemoryMediaTile(
                                        asset = asset,
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(24.dp)),
                                        videoSize = 40.dp,
                                        onClick = { lightboxIndex = i + 1 },
                                    )
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }

                        assets.drop(3).forEachIndexed { i, asset ->
                            val ratio = if (i % 2 == 0) 16f / 9f else 4f / 3f
                            MemoryMediaTile(
                                asset = asset,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(ratio)
                                    .clip(RoundedCornerShape(28.dp)),
                                videoSize = 48.dp,
                                onClick = { lightboxIndex = i + 3 },
                            )
                        }
                    }
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .width(32.dp)
                            .height(1.dp)
                            .background(SoftRoseFill),
                    )
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = Rose.copy(alpha = 0.35f),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        "$displayDate · 永久珍藏",
                        color = Stone.copy(alpha = 0.35f),
                        fontSize = 9.sp,
                        letterSpacing = 4.sp,
                        fontWeight = FontWeight.Light,
                    )
                }
            }
        }
    }

    lightboxIndex?.let { start ->
        MemoryLightbox(
            assets = assets,
            initialIndex = start,
            onDismiss = { lightboxIndex = null },
        )
    }
}

@Composable
private fun GlassIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.2f))
            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun MemoryMediaTile(
    asset: MediaAssetPart,
    modifier: Modifier = Modifier,
    videoSize: Dp,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val url = asset.previewUrl
    Box(
        modifier
            .background(Blush)
            .clickable(onClick = onClick),
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = listMediaImageRequest(context, url, asset.assetId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (asset.type == MediaType.VIDEO) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(videoSize)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = "播放",
                        tint = Color.White,
                        modifier = Modifier.size(videoSize * 0.4f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryLightbox(
    assets: List<MediaAssetPart>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    if (assets.isEmpty()) {
        onDismiss()
        return
    }
    val index = initialIndex.coerceIn(0, assets.lastIndex)
    val asset = assets[index]
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (asset.type == MediaType.VIDEO) {
                val url = asset.url.ifBlank { asset.previewUrl }
                if (url.isNotBlank()) {
                    VideoPreviewPage(
                        url = url,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .clip(RoundedCornerShape(16.dp)),
                    )
                }
            } else {
                val context = LocalContext.current
                val url = asset.url.ifBlank { asset.previewUrl }
                if (url.isNotBlank()) {
                    AsyncImage(
                        model = listMediaImageRequest(context, url, "${asset.assetId}-lightbox"),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .clip(RoundedCornerShape(16.dp)),
                    )
                }
            }
        }
    }
}

@Composable
fun MediaPreviewDialog(
    assets: List<MediaAssetPart>,
    initialIndex: Int = 0,
    @Suppress("UNUSED_PARAMETER") caption: String = "",
    @Suppress("UNUSED_PARAMETER") mediaDate: String = "",
    onDismiss: () -> Unit,
) {
    // 添加/编辑页仍复用全屏预览
    if (assets.isEmpty()) {
        onDismiss()
        return
    }
    MemoryLightbox(
        assets = assets,
        initialIndex = initialIndex,
        onDismiss = onDismiss,
    )
}

@Composable
private fun VideoPreviewPage(
    url: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(PlayerMediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = true
                this.player = player
            }
        },
        update = { it.player = player },
        modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f),
    )
}

private fun formatMemoryDate(raw: String): String = runCatching {
    val date = LocalDate.parse(raw.take(10))
    date.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
}.getOrDefault(raw.replace('-', '.'))
