@file:OptIn(ExperimentalMaterial3Api::class)

package com.lover.app.feature.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.lover.app.core.design.Blush
import com.lover.app.core.design.DeepRose
import com.lover.app.core.design.Peach
import com.lover.app.core.design.Rose
import com.lover.app.core.design.SoftSurface
import com.lover.app.core.design.Stone
import com.lover.app.core.design.WarmBackground
import com.lover.app.core.media.listMediaImageRequest
import com.lover.app.core.model.CoupleMember
import com.lover.app.core.model.MediaAssetPart
import com.lover.app.core.model.MediaItem
import com.lover.app.core.model.MediaType

@Composable
fun MediaDetailScreen(
    item: MediaItem,
    members: List<CoupleMember>,
    onClose: () -> Unit,
    onEdit: () -> Unit,
) {
    BackHandler(onBack = onClose)
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    val assets = item.assets.sortedBy { it.sortOrder }
    val uploaderName = members.firstOrNull { it.id == item.uploaderId }?.nickname
        ?: if (item.uploaderId.isNullOrBlank()) "我们" else "TA"
    val typeLabel = when {
        assets.isEmpty() -> "空"
        assets.size == 1 && assets.first().type == MediaType.VIDEO -> "视频"
        assets.size == 1 -> "照片"
        else -> "共 ${assets.size} 项"
    }

    Scaffold(
        containerColor = WarmBackground,
        topBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Blush.copy(alpha = 0.85f), WarmBackground)),
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = DeepRose)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text("时光详情", style = MaterialTheme.typography.titleLarge, color = DeepRose)
                        Text("$typeLabel · $uploaderName", style = MaterialTheme.typography.labelSmall, color = Stone)
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Rounded.Edit, "编辑", tint = DeepRose.copy(alpha = 0.85f))
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (assets.size <= 1) {
                val asset = assets.firstOrNull()
                Surface(
                    onClick = { if (asset != null) previewIndex = 0 },
                    shape = RoundedCornerShape(28.dp),
                    color = SoftSurface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    MediaAssetThumb(
                        asset = asset,
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.85f),
                        showHint = true,
                    )
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                ) {
                    itemsIndexed(assets, key = { _, a -> a.id.ifBlank { a.assetId } }) { index, asset ->
                        Surface(
                            onClick = { previewIndex = index },
                            shape = RoundedCornerShape(24.dp),
                            color = SoftSurface,
                        ) {
                            Box {
                                MediaAssetThumb(
                                    asset = asset,
                                    modifier = Modifier.size(148.dp, 172.dp),
                                    showHint = false,
                                )
                                Text(
                                    "${index + 1}/${assets.size}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
                Text("左右滑动查看，点击可全屏预览", style = MaterialTheme.typography.labelSmall, color = Stone)
            }

            if (item.caption.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("这一刻想说…", style = MaterialTheme.typography.labelMedium, color = Stone)
                    Text(
                        item.caption,
                        style = MaterialTheme.typography.bodyLarge,
                        color = DeepRose,
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SoftSurface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailMetaRow("记录日期", item.mediaDate)
                    DetailMetaRow("媒体", typeLabel)
                    DetailMetaRow("上传者", uploaderName)
                    DetailMetaRow("添加于", item.createdAt.take(10).ifBlank { "—" })
                }
            }
        }
    }

    previewIndex?.let { start ->
        MediaPreviewDialog(
            assets = assets,
            initialIndex = start,
            caption = item.caption,
            mediaDate = item.mediaDate,
            onDismiss = { previewIndex = null },
        )
    }
}

@Composable
private fun MediaAssetThumb(
    asset: MediaAssetPart?,
    modifier: Modifier = Modifier,
    showHint: Boolean,
) {
    val context = LocalContext.current
    Box(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Blush),
    ) {
        when {
            asset == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无媒体", color = Stone)
                }
            }
            asset.type == MediaType.VIDEO && asset.previewUrl.isBlank() -> {
                Box(Modifier.fillMaxSize().background(Peach.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Videocam, null, tint = Rose, modifier = Modifier.size(48.dp))
                }
            }
            else -> {
                val url = asset.previewUrl
                if (url.isNotBlank()) {
                    AsyncImage(
                        model = listMediaImageRequest(context, url, asset.assetId),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        if (asset?.type == MediaType.VIDEO) {
            Icon(
                Icons.Rounded.PlayCircle,
                "播放",
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(56.dp),
            )
        }
        if (showHint && asset != null) {
            Text(
                if (asset.type == MediaType.VIDEO) "点击播放" else "点击查看",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun DetailMetaRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Stone, style = MaterialTheme.typography.bodySmall)
        Text(value, fontWeight = FontWeight.Medium, color = DeepRose)
    }
}

@Composable
fun MediaPreviewDialog(
    assets: List<MediaAssetPart>,
    initialIndex: Int = 0,
    caption: String = "",
    mediaDate: String = "",
    onDismiss: () -> Unit,
) {
    if (assets.isEmpty()) {
        onDismiss()
        return
    }
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, assets.lastIndex),
        pageCount = { assets.size },
    )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val asset = assets[page]
                if (asset.type == MediaType.VIDEO) {
                    VideoPreviewPage(url = asset.url.ifBlank { asset.previewUrl })
                } else {
                    val context = LocalContext.current
                    val url = asset.url.ifBlank { asset.previewUrl }
                    AsyncImage(
                        model = listMediaImageRequest(context, url, "${asset.assetId}-full"),
                        contentDescription = caption,
                        Modifier
                            .fillMaxSize()
                            .clickable(onClick = onDismiss),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
            ) {
                Icon(Icons.Rounded.Close, "关闭", tint = Color.White)
            }
            if (assets.size > 1) {
                Text(
                    "${pagerState.currentPage + 1} / ${assets.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 16.dp)
                        .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            if (caption.isNotBlank() || mediaDate.isNotBlank()) {
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .9f))))
                        .padding(24.dp)
                        .navigationBarsPadding(),
                ) {
                    if (caption.isNotBlank()) {
                        Text(caption, color = Color.White, style = MaterialTheme.typography.titleLarge)
                    }
                    Text(mediaDate, color = Color.White.copy(alpha = .7f))
                }
            }
        }
    }
}

@Composable
private fun VideoPreviewPage(url: String) {
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
        modifier = Modifier.fillMaxSize(),
    )
}
