package com.lover.app.feature.main

import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lover.app.core.design.Blush
import com.lover.app.core.design.Peach
import com.lover.app.core.design.Rose
import com.lover.app.core.media.listMediaImageRequest
import com.lover.app.core.model.MediaAssetPart
import com.lover.app.core.model.MediaType
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

internal const val ComposerMaxMediaPick = 20

internal sealed class MediaDraftCell {
    abstract val key: String

    data class Local(val uri: Uri) : MediaDraftCell() {
        override val key: String get() = "local:${uri}"
    }

    data class Remote(val part: MediaAssetPart) : MediaDraftCell() {
        override val key: String
            get() = "remote:${part.id.ifBlank { part.assetId }}"
    }
}

@Composable
internal fun ReorderableMediaDraftGrid(
    cells: List<MediaDraftCell>,
    canReorder: Boolean,
    onReorder: (from: Int, to: Int) -> Unit,
    onPreview: (index: Int) -> Unit,
    canRemoveAt: (index: Int) -> Boolean,
    onRemoveAt: (index: Int) -> Unit,
) {
    if (cells.isEmpty()) return

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val gridState = rememberLazyGridState()
    val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
        onReorder(from.index, to.index)
    }
    var draggingKey by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val gap = 8.dp
        val cellSize = (maxWidth - gap * 2) / 3
        val rows = (cells.size + 2) / 3
        val gridHeight = cellSize * rows + gap * (rows - 1).coerceAtLeast(0)

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            userScrollEnabled = false,
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight),
        ) {
            itemsIndexed(cells, key = { _, cell -> cell.key }) { index, cell ->
                ReorderableItem(reorderState, key = cell.key) { isDragging ->
                    LaunchedEffect(isDragging, cell.key) {
                        if (isDragging) {
                            draggingKey = cell.key
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else if (draggingKey == cell.key) {
                            draggingKey = null
                        }
                    }
                    val scale by animateFloatAsState(
                        targetValue = if (isDragging) 1.08f else 1f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "mediaDragScale",
                    )
                    val isVideo = when (cell) {
                        is MediaDraftCell.Local ->
                            context.contentResolver.getType(cell.uri)?.startsWith("video/") == true
                        is MediaDraftCell.Remote -> cell.part.type == MediaType.VIDEO
                    }
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                alpha = if (draggingKey != null && !isDragging) 0.72f else 1f
                            }
                            .clip(RoundedCornerShape(16.dp))
                            .background(Blush)
                            .then(
                                if (canReorder) {
                                    Modifier.longPressDraggableHandle()
                                } else {
                                    Modifier
                                },
                            )
                            .clickable { onPreview(index) },
                    ) {
                        when (cell) {
                            is MediaDraftCell.Local -> {
                                if (isVideo) {
                                    Box(
                                        Modifier.fillMaxSize().background(Peach.copy(alpha = 0.45f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        AsyncImage(
                                            model = cell.uri,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                        Icon(
                                            Icons.Rounded.PlayCircle,
                                            null,
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp),
                                        )
                                    }
                                } else {
                                    AsyncImage(
                                        model = cell.uri,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            }
                            is MediaDraftCell.Remote -> {
                                val url = cell.part.previewUrl
                                if (url.isNotBlank()) {
                                    AsyncImage(
                                        model = listMediaImageRequest(context, url, cell.part.assetId),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                                if (isVideo) {
                                    Icon(
                                        Icons.Rounded.PlayCircle,
                                        null,
                                        tint = Color.White,
                                        modifier = Modifier.align(Alignment.Center).size(28.dp),
                                    )
                                }
                            }
                        }
                        if (canRemoveAt(index)) {
                            IconButton(
                                onClick = { onRemoveAt(index) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(28.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Cancel,
                                    "移除",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                        .padding(2.dp)
                                        .size(16.dp),
                                )
                            }
                        }
                        Text(
                            "${index + 1}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                                .background(Rose.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}
