package com.lover.app.feature.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lover.app.R
import com.lover.app.core.design.EBGaramond
import com.lover.app.core.design.LocalMood
import com.lover.app.core.design.LoverAppName
import com.lover.app.core.design.LoverLogo
import com.lover.app.core.media.listMediaImageRequest
import com.lover.app.core.model.DailyQuote
import com.lover.app.core.model.MediaItem
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.delay

private const val GlimpseIntervalMs = 7_000L
private const val GlimpseAnimMs = 920
private val GlimpseEasing = CubicBezierEasing(0.33f, 0.0f, 0.2f, 1.0f)

private enum class GlimpseSlide { Featured, Vertical, Horizontal }

private val ChineseWeekdays = mapOf(
    DayOfWeek.MONDAY to "周一",
    DayOfWeek.TUESDAY to "周二",
    DayOfWeek.WEDNESDAY to "周三",
    DayOfWeek.THURSDAY to "周四",
    DayOfWeek.FRIDAY to "周五",
    DayOfWeek.SATURDAY to "周六",
    DayOfWeek.SUNDAY to "周日",
)

private val ChineseMonths = arrayOf(
    "一月", "二月", "三月", "四月", "五月", "六月",
    "七月", "八月", "九月", "十月", "十一月", "十二月",
)

/** Latin-only (or mostly Latin) lines render in italic under Soleil mood. */
internal fun quoteLooksEnglish(text: String): Boolean {
    val letters = text.filter { it.isLetter() }
    if (letters.isEmpty()) return false
    val latin = letters.count { it.code < 0x0080 }
    val cjk = letters.count { it.code in 0x4E00..0x9FFF }
    return latin > 0 && cjk == 0
}

@Composable
internal fun HomeTopBar(
    modifier: Modifier = Modifier,
) {
    val mood = LocalMood.current
    val today = remember { LocalDate.now() }
    val weekday = ChineseWeekdays[today.dayOfWeek].orEmpty()
    val monthLabel = ChineseMonths[today.monthValue - 1]

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LoverLogo(size = 40.dp)
                LoverAppName(fontSize = 42.sp)
            }
            Text(
                text = mood.tagline,
                style = MaterialTheme.typography.labelSmall,
                color = mood.stone.copy(alpha = 0.8f),
                fontWeight = FontWeight.Light,
                letterSpacing = if (mood.solo) 1.2.sp else 2.sp,
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = weekday,
                style = MaterialTheme.typography.labelSmall,
                color = mood.soft.copy(alpha = 0.85f),
                fontWeight = FontWeight.Light,
                letterSpacing = 3.sp,
            )
            Text(
                text = today.dayOfMonth.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = mood.accent.copy(alpha = 0.88f),
                fontSize = 30.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = (-0.5).sp,
            )
            Text(
                text = "$monthLabel · ${today.year}",
                style = MaterialTheme.typography.labelSmall,
                color = mood.stone.copy(alpha = 0.65f),
                fontWeight = FontWeight.Light,
                letterSpacing = 1.2.sp,
            )
        }
    }
}

@Composable
internal fun DailyQuoteCard(
    quote: DailyQuote?,
    modifier: Modifier = Modifier,
) {
    val mood = LocalMood.current
    val fallback = if (mood.solo) {
        "把今天过成日记里值得留下的一页。"
    } else {
        "平凡的日子，因为有你，每一天都值得被记住。"
    }
    val text = quote?.text?.takeIf { it.isNotBlank() } ?: fallback
    val author = quote?.author?.takeIf { it.isNotBlank() }
    val englishItalic = mood.solo && quoteLooksEnglish(text)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(40.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        mood.blush.copy(alpha = 0.95f),
                        if (mood.solo) mood.softSurface else Color.White,
                        mood.peach.copy(alpha = if (mood.solo) 0.55f else 0.45f),
                    ),
                ),
            )
            .padding(horizontal = 26.dp, vertical = 24.dp),
    ) {
        Text(
            text = "“",
            color = mood.soft.copy(alpha = 0.14f),
            fontSize = 72.sp,
            fontWeight = FontWeight.Light,
            fontFamily = if (mood.solo) EBGaramond else null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 4.dp),
        )
        Column(modifier = Modifier.fillMaxWidth().padding(end = 12.dp)) {
            Text(
                text = "每日寄语",
                color = mood.soft.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 4.sp,
                fontWeight = FontWeight.Light,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = text,
                color = mood.stone.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (englishItalic) EBGaramond else null,
                fontStyle = if (englishItalic) FontStyle.Italic else FontStyle.Normal,
                fontWeight = FontWeight.Light,
                lineHeight = 26.sp,
            )
            if (author != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "— $author",
                    color = mood.stone.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Light,
                )
            }
        }
        if (mood.solo) {
            Image(
                painter = painterResource(R.drawable.ic_feather),
                contentDescription = null,
                colorFilter = ColorFilter.tint(mood.accent.copy(alpha = 0.35f)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(20.dp),
            )
        } else {
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = null,
                tint = mood.soft.copy(alpha = 0.28f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(18.dp),
            )
        }
    }
}

/**
 * Bento recent moments: up to 8 cover thumbs.
 * Featured auto-advances every 7s; all slots animate with a forward-rotate feel
 * (exit toward top-start, enter from bottom-end) instead of hard cuts.
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

    var featuredIdx by remember(snapshots.map { it.id }) { mutableIntStateOf(0) }

    fun jumpTo(index: Int) {
        if (index == featuredIdx || snapshots.isEmpty()) return
        featuredIdx = index.coerceIn(0, snapshots.lastIndex)
    }

    LaunchedEffect(featuredIdx, snapshots.size) {
        if (snapshots.size <= 1) return@LaunchedEffect
        delay(GlimpseIntervalMs)
        featuredIdx = (featuredIdx + 1) % snapshots.size
    }

    val rotated = remember(snapshots, featuredIdx) {
        List(snapshots.size) { offset -> snapshots[(featuredIdx + offset) % snapshots.size] }
    }

    val mood = LocalMood.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "近期掠影",
                style = MaterialTheme.typography.titleMedium,
                color = mood.accent.copy(alpha = 0.55f),
                letterSpacing = 2.sp,
            )
            Text(
                text = "查看全部 →",
                style = MaterialTheme.typography.labelSmall,
                color = mood.stone.copy(alpha = 0.55f),
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

        val count = rotated.size
        val sideCount = (count - 1).coerceIn(0, 2)
        val midSlots = (3 until minOf(6, count)).toList()
        val bottomSlots = (6 until count).toList()

        // Top: featured (+ up to 2 side thumbs when available)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (sideCount > 0) 230.dp else 210.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlimpseAnimatedSlot(
                item = rotated[0],
                slide = GlimpseSlide.Featured,
                onClick = { onMedia(rotated[0]) },
                modifier = Modifier
                    .weight(if (sideCount > 0) 2f else 1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(32.dp)),
                titleStyleLarge = true,
                showDate = true,
                overlay = {
                    if (snapshots.size > 1) {
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
                },
            )
            if (sideCount > 0) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    repeat(sideCount) { i ->
                        val slot = i + 1
                        GlimpseAnimatedSlot(
                            item = rotated[slot],
                            slide = GlimpseSlide.Vertical,
                            onClick = { onMedia(rotated[slot]) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp)),
                        )
                    }
                }
            }
        }

        // Middle row: indices 3–5
        if (midSlots.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                midSlots.forEach { slot ->
                    GlimpseAnimatedSlot(
                        item = rotated[slot],
                        slide = GlimpseSlide.Horizontal,
                        onClick = { onMedia(rotated[slot]) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp)),
                    )
                }
            }
        }

        // Bottom row: indices 6–7
        if (bottomSlots.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                bottomSlots.forEach { slot ->
                    GlimpseAnimatedSlot(
                        item = rotated[slot],
                        slide = GlimpseSlide.Horizontal,
                        onClick = { onMedia(rotated[slot]) },
                        modifier = Modifier
                            .weight(if (slot == 7) 2f else 1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun GlimpseAnimatedSlot(
    item: MediaItem?,
    slide: GlimpseSlide,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleStyleLarge: Boolean = false,
    showDate: Boolean = false,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
) {
    val enterSpec = tween<Float>(GlimpseAnimMs, easing = GlimpseEasing)
    val exitSpec = tween<Float>(GlimpseAnimMs, easing = GlimpseEasing)
    val offsetEnter = tween<IntOffset>(GlimpseAnimMs, easing = GlimpseEasing)
    val offsetExit = tween<IntOffset>(GlimpseAnimMs, easing = GlimpseEasing)

    Box(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        AnimatedContent(
            targetState = item,
            contentKey = { it?.id ?: "empty" },
            transitionSpec = {
                val enter = when (slide) {
                    GlimpseSlide.Featured ->
                        fadeIn(enterSpec) + slideInHorizontally(offsetEnter) { it / 8 }
                    GlimpseSlide.Vertical ->
                        fadeIn(enterSpec) + slideInVertically(offsetEnter) { it / 5 }
                    GlimpseSlide.Horizontal ->
                        fadeIn(enterSpec) + slideInHorizontally(offsetEnter) { it / 6 }
                }
                val exit = when (slide) {
                    GlimpseSlide.Featured ->
                        fadeOut(exitSpec) + slideOutHorizontally(offsetExit) { -it / 10 }
                    GlimpseSlide.Vertical ->
                        fadeOut(exitSpec) + slideOutVertically(offsetExit) { -it / 5 }
                    GlimpseSlide.Horizontal ->
                        fadeOut(exitSpec) + slideOutHorizontally(offsetExit) { -it / 6 }
                }
                (enter togetherWith exit).using(SizeTransform(clip = true))
            },
            label = "glimpseSlot",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            GlimpseThumb(
                item = current,
                modifier = Modifier.fillMaxSize(),
                titleStyleLarge = titleStyleLarge,
                showDate = showDate,
            )
        }
        overlay?.invoke(this)
    }
}

@Composable
private fun GlimpseThumb(
    item: MediaItem?,
    modifier: Modifier = Modifier,
    titleStyleLarge: Boolean = false,
    showDate: Boolean = false,
) {
    val context = LocalContext.current
    val cover = item?.cover
    val url = item?.thumbnailUrl ?: item?.url
    val mood = LocalMood.current
    Box(modifier = modifier.background(mood.blush)) {
        if (item != null && cover != null && !url.isNullOrBlank()) {
            AsyncImage(
                model = listMediaImageRequest(context, url, cover.assetId),
                contentDescription = item.caption,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
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
                    ),
            ) {
                Text(
                    text = item.caption.ifBlank { "未命名时光" },
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
