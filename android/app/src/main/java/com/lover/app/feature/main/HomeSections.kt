package com.lover.app.feature.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lover.app.core.design.Blush
import com.lover.app.core.design.DeepRose
import com.lover.app.core.design.Peach
import com.lover.app.core.design.Rose
import com.lover.app.core.design.Stone
import com.lover.app.core.media.listMediaImageRequest
import com.lover.app.core.model.DailyQuote
import com.lover.app.core.model.MediaItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun DailyQuoteCard(
    quote: DailyQuote?,
    modifier: Modifier = Modifier,
) {
    val text = quote?.text?.takeIf { it.isNotBlank() }
        ?: "\u5e73\u51e1\u7684\u65e5\u5b50\uff0c\u56e0\u4e3a\u6709\u4f60\uff0c\u6bcf\u4e00\u5929\u90fd\u503c\u5f97\u88ab\u8bb0\u4f4f\u3002"
    val author = quote?.author?.takeIf { it.isNotBlank() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(40.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Blush.copy(alpha = 0.95f),
                        Color.White,
                        Peach.copy(alpha = 0.45f),
                    ),
                ),
            )
            .padding(horizontal = 26.dp, vertical = 24.dp),
    ) {
        Text(
            text = "\u201c",
            color = Rose.copy(alpha = 0.12f),
            fontSize = 72.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 4.dp),
        )
        Column(modifier = Modifier.fillMaxWidth().padding(end = 12.dp)) {
            Text(
                text = "\u6bcf\u65e5\u5bc4\u8bed",
                color = Rose.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 4.sp,
                fontWeight = FontWeight.Light,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = text,
                color = Stone.copy(alpha = 0.88f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Light,
                lineHeight = 26.sp,
            )
            if (author != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "\u2014 $author",
                    color = Stone.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Light,
                )
            }
        }
        Icon(
            Icons.Rounded.Favorite,
            contentDescription = null,
            tint = Rose.copy(alpha = 0.28f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(18.dp),
        )
    }
}

/**
 * Bento recent moments: up to 8 cover thumbs.
 * Featured (row1 left) auto-advances every 3.5s; the whole window rotates forward
 * so side tiles also step to the next items in order.
 */
@Composable
internal fun RecentGlimpseBento(
    media: List<MediaItem>,
    onMedia: (MediaItem) -> Unit,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshots = remember(media) { media.take(8) }
    if (snapshots.isEmpty()) return

    val scope = rememberCoroutineScope()
    var featuredIdx by remember(snapshots.map { it.id }) { mutableIntStateOf(0) }
    var fadeVisible by remember { mutableStateOf(true) }
    val fadeAlpha by animateFloatAsState(
        targetValue = if (fadeVisible) 1f else 0f,
        animationSpec = tween(400),
        label = "glimpseFade",
    )

    fun jumpTo(index: Int) {
        if (index == featuredIdx || snapshots.isEmpty()) return
        scope.launch {
            fadeVisible = false
            delay(400)
            featuredIdx = index.coerceIn(0, snapshots.lastIndex)
            fadeVisible = true
        }
    }

    LaunchedEffect(featuredIdx, snapshots.size) {
        if (snapshots.size <= 1) return@LaunchedEffect
        delay(3500)
        fadeVisible = false
        delay(400)
        featuredIdx = (featuredIdx + 1) % snapshots.size
        fadeVisible = true
    }

    val rotated = remember(snapshots, featuredIdx) {
        List(snapshots.size) { offset -> snapshots[(featuredIdx + offset) % snapshots.size] }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "\u8fd1\u671f\u63a0\u5f71",
                style = MaterialTheme.typography.titleMedium,
                color = DeepRose.copy(alpha = 0.55f),
                letterSpacing = 2.sp,
            )
            Text(
                text = "\u67e5\u770b\u5168\u90e8 \u2192",
                style = MaterialTheme.typography.labelSmall,
                color = Stone.copy(alpha = 0.55f),
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onViewAll,
                ),
            )
        }
        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(32.dp))
                    .clickable { rotated.getOrNull(0)?.let(onMedia) },
            ) {
                GlimpseThumb(
                    item = rotated.getOrNull(0),
                    modifier = Modifier.fillMaxSize(),
                    titleStyleLarge = true,
                    showDate = true,
                    contentAlpha = fadeAlpha,
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    snapshots.indices.forEach { index ->
                        val selected = index == featuredIdx
                        Box(
                            modifier = Modifier
                                .height(5.dp)
                                .width(if (selected) 14.dp else 5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) Color.White.copy(alpha = 0.9f)
                                    else Color.White.copy(alpha = 0.4f),
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { jumpTo(index) },
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlimpseThumb(
                    item = rotated.getOrNull(1),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { rotated.getOrNull(1)?.let(onMedia) },
                )
                GlimpseThumb(
                    item = rotated.getOrNull(2),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { rotated.getOrNull(2)?.let(onMedia) },
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            listOf(3, 4, 5).forEach { slot ->
                GlimpseThumb(
                    item = rotated.getOrNull(slot),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { rotated.getOrNull(slot)?.let(onMedia) },
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlimpseThumb(
                item = rotated.getOrNull(6),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { rotated.getOrNull(6)?.let(onMedia) },
            )
            GlimpseThumb(
                item = rotated.getOrNull(7),
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { rotated.getOrNull(7)?.let(onMedia) },
            )
        }
    }
}

@Composable
private fun GlimpseThumb(
    item: MediaItem?,
    modifier: Modifier = Modifier,
    titleStyleLarge: Boolean = false,
    showDate: Boolean = false,
    contentAlpha: Float = 1f,
) {
    val context = LocalContext.current
    val cover = item?.cover
    val url = item?.thumbnailUrl ?: item?.url
    Box(modifier = modifier.background(Blush)) {
        if (item != null && cover != null && !url.isNullOrBlank()) {
            AsyncImage(
                model = listMediaImageRequest(context, url, cover.assetId),
                contentDescription = item.caption,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(contentAlpha),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.5f),
                    ),
                ),
        )
        if (item != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        horizontal = if (titleStyleLarge) 14.dp else 10.dp,
                        vertical = if (titleStyleLarge) 12.dp else 8.dp,
                    )
                    .alpha(contentAlpha),
            ) {
                Text(
                    text = item.caption.ifBlank { "\u672a\u547d\u540d\u65f6\u5149" },
                    color = Color.White,
                    style = if (titleStyleLarge) {
                        MaterialTheme.typography.labelLarge
                    } else {
                        MaterialTheme.typography.labelSmall
                    },
                    fontWeight = FontWeight.Light,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showDate) {
                    Text(
                        text = item.mediaDate,
                        color = Color.White.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Light,
                    )
                }
            }
        }
    }
}
