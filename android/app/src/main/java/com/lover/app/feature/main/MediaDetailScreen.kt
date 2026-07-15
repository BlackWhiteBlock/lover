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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.lover.app.core.design.LoverDateField
import com.lover.app.core.design.Peach
import com.lover.app.core.design.Rose
import com.lover.app.core.design.SoftOutline
import com.lover.app.core.design.SoftSurface
import com.lover.app.core.design.SoftTextField
import com.lover.app.core.design.Stone
import com.lover.app.core.design.WarmBackground
import com.lover.app.core.model.CoupleMember
import com.lover.app.core.model.MediaAssetPart
import com.lover.app.core.model.MediaItem
import com.lover.app.core.model.MediaType
import java.time.LocalDate

@Composable
fun MediaDetailScreen(
    item: MediaItem,
    members: List<CoupleMember>,
    onClose: () -> Unit,
    onSave: (caption: String, date: String) -> Unit,
    onDelete: () -> Unit,
) {
    BackHandler(onBack = onClose)
    var caption by rememberSaveable(item.id) { mutableStateOf(item.caption) }
    var date by rememberSaveable(item.id) { mutableStateOf(item.mediaDate) }
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val assets = item.assets.sortedBy { it.sortOrder }
    val uploaderName = members.firstOrNull { it.id == item.uploaderId }?.nickname
        ?: if (item.uploaderId.isNullOrBlank()) "我们" else "TA"
    val dirty = caption.trim() != item.caption.trim() || date != item.mediaDate
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
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Rounded.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        bottomBar = {
            Surface(color = WarmBackground.copy(alpha = 0.96f)) {
                Button(
                    onClick = { onSave(caption, date) },
                    enabled = dirty,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Rose,
                        disabledContainerColor = SoftOutline,
                    ),
                ) {
                    Text(if (dirty) "保存修改" else "未修改")
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
                .padding(bottom = 16.dp),
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

            SoftTextField(
                value = caption,
                onValueChange = { caption = it.take(200) },
                label = "这一刻想说…",
                placeholder = "写给这段时光的一句话",
                singleLine = false,
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            LoverDateField(
                value = date,
                onValueChange = { date = it },
                label = "记录日期",
                maxDate = LocalDate.now(),
                modifier = Modifier.fillMaxWidth(),
                supportingText = "会作为这段回忆的日期标记",
            )

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SoftSurface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailMetaRow("媒体", typeLabel)
                    DetailMetaRow("上传者", uploaderName)
                    DetailMetaRow("添加于", item.createdAt.take(10).ifBlank { "—" })
                }
            }

            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("删除这段时光")
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
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = WarmBackground,
            title = { Text("删除这段时光？") },
            text = { Text("将删除整条时光及其中的全部照片/视频，确认删除吗？", color = Stone) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDelete = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = Stone),
                ) { Text("取消") }
            },
        )
    }
}

@Composable
private fun MediaAssetThumb(
    asset: MediaAssetPart?,
    modifier: Modifier = Modifier,
    showHint: Boolean,
) {
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
                AsyncImage(
                    model = asset.previewUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
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
                if (asset.type == MediaType.VIDEO) "点击播放" else "点击查看原图",
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
                    VideoPreviewPage(url = asset.url)
                } else {
                    AsyncImage(
                        asset.url,
                        caption,
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
