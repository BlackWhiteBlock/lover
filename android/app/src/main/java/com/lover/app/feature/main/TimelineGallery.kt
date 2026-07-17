package com.lover.app.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lover.app.core.design.LocalMood
import com.lover.app.core.media.listMediaImageRequest
import com.lover.app.core.model.MediaAssetPart
import com.lover.app.core.model.MediaItem
import com.lover.app.core.model.MediaType

private enum class TimelineCardVariant { SINGLE, DUO, STRIP }

/** 180-250dp rhythm; adjacent heights never match. */
private val HeightBeat = listOf(210, 240, 230, 180, 220, 250, 200, 215, 190)

private data class TimelineCardPlan(
    val item: MediaItem,
    val variant: TimelineCardVariant,
    val heightDp: Int,
)

/**
 * Pick card shape strictly by media count first.
 * Rhythm only switches among variants that the item can actually support ?
 * never force Duo/Strip onto a single-asset memory.
 */
private fun planTimelineCards(items: List<MediaItem>): List<TimelineCardPlan> {
    var previous: TimelineCardVariant? = null
    var previousHeight = 0
    return items.mapIndexed { index, item ->
        val variant = resolveVariant(item, previous)
        var height = HeightBeat[index % HeightBeat.size]
        if (item.caption.trim().length > 50) height = (height + 20).coerceAtMost(270)
        if (height == previousHeight) {
            height = (height + 20).coerceIn(180, 270)
            if (height == previousHeight) height = if (height >= 230) height - 30 else height + 30
        }
        previous = variant
        previousHeight = height
        TimelineCardPlan(item, variant, height)
    }
}

private fun mediaCount(item: MediaItem): Int =
    item.assets.size.coerceAtLeast(if (item.cover != null) 1 else 0)

/** Variants this item is allowed to use based on asset count. */
private fun eligibleVariants(item: MediaItem): List<TimelineCardVariant> {
    val count = mediaCount(item)
    return when {
        count <= 1 -> listOf(TimelineCardVariant.SINGLE)
        count <= 3 -> listOf(TimelineCardVariant.DUO, TimelineCardVariant.SINGLE)
        else -> listOf(
            TimelineCardVariant.STRIP,
            TimelineCardVariant.DUO,
            TimelineCardVariant.SINGLE,
        )
    }
}

private fun preferredVariant(item: MediaItem): TimelineCardVariant {
    val count = mediaCount(item)
    val hasVideo = item.assets.any { it.type == MediaType.VIDEO }
    return when {
        count <= 1 -> TimelineCardVariant.SINGLE
        hasVideo -> TimelineCardVariant.DUO
        count >= 4 -> TimelineCardVariant.STRIP
        else -> TimelineCardVariant.DUO
    }
}

private fun resolveVariant(item: MediaItem, previous: TimelineCardVariant?): TimelineCardVariant {
    val eligible = eligibleVariants(item)
    val preferred = preferredVariant(item).takeIf { it in eligible } ?: eligible.first()
    if (previous == null || preferred != previous) return preferred
    // Only alternate when another eligible shape exists; single-asset stays Single.
    return eligible.firstOrNull { it != previous } ?: preferred
}

@Composable
internal fun TimelineGalleryContent(
    media: List<MediaItem>,
    pendingUploads: List<PendingMediaUpload>,
    onMedia: (MediaItem) -> Unit,
    uploadingCard: @Composable (PendingMediaUpload) -> Unit,
) {
    val featured = media.firstOrNull()
    val restPlans = remember(media) {
        planTimelineCards(media.drop(1))
    }
    val leftPlans = restPlans.filterIndexed { index, _ -> index % 2 == 0 }
    val rightPlans = restPlans.filterIndexed { index, _ -> index % 2 == 1 }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (pendingUploads.isNotEmpty()) {
            item(key = "pending-row") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(pendingUploads, key = { "pending-${it.id}" }) { pending ->
                        Box(modifier = Modifier.width(148.dp)) {
                            uploadingCard(pending)
                        }
                    }
                }
            }
        }

        if (featured != null) {
            item(key = "featured-${featured.id}") {
                FeaturedTimelineCard(
                    item = featured,
                    onClick = { onMedia(featured) },
                )
            }
        }

        if (restPlans.isNotEmpty()) {
            item(key = "stagger-columns") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        leftPlans.forEach { plan ->
                            TimelineVariantCard(
                                plan = plan,
                                onClick = { onMedia(plan.item) },
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        rightPlans.forEach { plan ->
                            TimelineVariantCard(
                                plan = plan,
                                onClick = { onMedia(plan.item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineVariantCard(
    plan: TimelineCardPlan,
    onClick: () -> Unit,
) {
    val height = plan.heightDp.dp
    when (plan.variant) {
        TimelineCardVariant.SINGLE -> CardSingle(plan.item, height, onClick)
        TimelineCardVariant.STRIP -> CardStrip(plan.item, height, onClick)
        TimelineCardVariant.DUO -> CardDuo(plan.item, height, onClick)
    }
}

@Composable
private fun FeaturedTimelineCard(
    item: MediaItem,
    onClick: () -> Unit,
) {
    val sorted = remember(item.assets) { item.assets.sortedBy { it.sortOrder } }
    val strip = sorted.take(4)
    val overflow = (sorted.size - 4).coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(40.dp))
            .background(LocalMood.current.blush)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.65f),
        ) {
            MediaThumb(part = item.cover, contentDescription = item.caption)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.5f),
                        ),
                    ),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "\u7cbe\u9009",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.sp,
                )
                Text(
                    item.caption.ifBlank { item.mediaDate },
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.assets.any { it.type == MediaType.VIDEO }) {
                VideoBadge(modifier = Modifier.align(Alignment.TopStart).padding(14.dp))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.35f)
                .background(Color.White.copy(alpha = 0.72f))
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            strip.forEachIndexed { index, part ->
                Box(
                    modifier = Modifier
                        .weight(if (index == 0) 1.5f else 1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp)),
                ) {
                    MediaThumb(part = part)
                    if (part.type == MediaType.VIDEO) {
                        VideoScrim()
                    }
                }
            }
            if (overflow > 0) {
                val extra = sorted.getOrNull(4)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (extra != null) {
                        MediaThumb(part = extra, alpha = 0.45f)
                    } else {
                        Box(Modifier.fillMaxSize().background(LocalMood.current.blush))
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                    )
                    Text(
                        "+$overflow",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Light,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .width(56.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    item.mediaDate,
                    color = LocalMood.current.stone.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.assetCount > 1) {
                    Text(
                        text = "${item.assetCount} \u5f20",
                        color = LocalMood.current.stone.copy(alpha = 0.45f),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun CardSingle(
    item: MediaItem,
    height: Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(28.dp))
            .background(LocalMood.current.blush)
            .clickable(onClick = onClick),
    ) {
        MediaThumb(part = item.cover, contentDescription = item.caption)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.65f),
                    ),
                ),
        )
        if (item.assets.any { it.type == MediaType.VIDEO }) {
            VideoBadge(modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                item.caption.ifBlank { "\u672a\u547d\u540d\u65f6\u5149" },
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.mediaDate,
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun CardStrip(
    item: MediaItem,
    height: Dp,
    onClick: () -> Unit,
) {
    val sorted = remember(item.assets) { item.assets.sortedBy { it.sortOrder } }
    val thumbs = sorted.drop(1).take(2)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.62f),
        ) {
            MediaThumb(part = item.cover, contentDescription = item.caption)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.28f),
                        ),
                    ),
            )
            if (item.assets.any { it.type == MediaType.VIDEO }) {
                VideoBadge(modifier = Modifier.align(Alignment.TopStart).padding(10.dp))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.38f)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(2) { index ->
                val part = thumbs.getOrNull(index)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(LocalMood.current.soft.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (part != null) {
                        MediaThumb(part = part)
                        if (part.type == MediaType.VIDEO) VideoScrim()
                    } else {
                        Icon(
                            Icons.Rounded.Image,
                            contentDescription = null,
                            tint = LocalMood.current.soft.copy(alpha = 0.35f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1.05f)
                    .fillMaxHeight()
                    .padding(start = 2.dp, bottom = 2.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    item.caption.ifBlank { "\u672a\u547d\u540d\u65f6\u5149" },
                    color = LocalMood.current.stone,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.mediaDate,
                    color = LocalMood.current.stone.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                )
            }
        }
    }
}

@Composable
private fun CardDuo(
    item: MediaItem,
    height: Dp,
    onClick: () -> Unit,
) {
    val sorted = remember(item.assets) { item.assets.sortedBy { it.sortOrder } }
    val top = sorted.firstOrNull()
    val bottom = sorted.getOrNull(1) ?: top

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(3f)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(LocalMood.current.blush),
        ) {
            MediaThumb(part = top, contentDescription = item.caption)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.18f),
                            1f to Color.Transparent,
                        ),
                    ),
            )
            Text(
                item.caption.ifBlank { "\u672a\u547d\u540d\u65f6\u5149" },
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(2f)
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .background(LocalMood.current.blush),
        ) {
            MediaThumb(part = bottom)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.4f),
                        ),
                    ),
            )
            Text(
                item.mediaDate,
                color = Color.White.copy(alpha = 0.65f),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            if (bottom?.type == MediaType.VIDEO) {
                VideoScrim(large = true)
            }
        }
    }
}

@Composable
private fun MediaThumb(
    part: MediaAssetPart?,
    contentDescription: String? = null,
    alpha: Float = 1f,
) {
    val context = LocalContext.current
    val url = part?.previewUrl.orEmpty()
    if (part != null && url.isNotBlank()) {
        AsyncImage(
            model = listMediaImageRequest(context, url, part.assetId),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha),
        )
    } else {
        Box(modifier = Modifier.fillMaxSize().background(LocalMood.current.blush))
    }
}

@Composable
private fun VideoBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.28f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.PlayArrow,
            contentDescription = "\u89c6\u9891",
            tint = Color.White,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun VideoScrim(large: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(if (large) 28.dp else 22.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.28f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(if (large) 16.dp else 12.dp),
            )
        }
    }
}
