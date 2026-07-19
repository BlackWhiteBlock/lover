package com.lover.app.core.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

private val QuietStroke = Color(0xFFFDA4AF)
private val ActiveRose = Color(0xFFFB7185)
private val BadgeStart = Color(0xFFF43F5E)
private val BadgeEnd = Color(0xFFFB7185)
private val IconBgTop = Color(0xFFFFF0F2)
private val IconBgBottom = Color(0xFFFFE4E8)

/** 弹跳 2.8s + 停歇 1.2s */
private const val BounceCycleMs = 4000
private const val BounceActiveMs = 2800
private const val PulseCycleMs = 2200
private const val PulsePhaseMs = 550
/** 第 3 颗 1.4s 起跳 + 1.8s 飞行 ≈ 3.2s，周期取 3.5s */
private const val SparkleCycleMs = 3500
private const val SparkleDurationMs = 1800
private const val SparkleStaggerMs = 700
private val SparkleAngles = floatArrayOf(-45f, 45f, 135f)

/**
 * 未读时光图标（对齐设计说明）。
 * 有未读：四层动画；无未读：透明底盘 + 浅玫瑰描边，静止。
 */
@Composable
fun UnreadMemoryIcon(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = count > 0
    var displayCount by remember { mutableIntStateOf(count.coerceAtLeast(0)) }
    val badgeScale = remember { Animatable(if (count > 0) 1f else 0f) }
    LaunchedEffect(count) {
        if (count > 0) displayCount = count
    }
    LaunchedEffect(active) {
        if (active) {
            badgeScale.snapTo(0f)
            badgeScale.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
        } else {
            badgeScale.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
        }
    }

    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            UnreadPulseRings()
            UnreadAnimatedBody(count = displayCount, badgeScale = badgeScale.value)
        } else {
            UnreadStaticBody(count = displayCount, badgeScale = badgeScale.value)
        }
    }
}

@Composable
private fun UnreadPulseRings() {
    val infinite = rememberInfiniteTransition(label = "unread-pulse")
    val p1 by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(PulseCycleMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring1",
    )
    val p2 by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(PulseCycleMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(PulsePhaseMs),
        ),
        label = "ring2",
    )
    PulseRing(progress = p1, maxScale = 1.7f, baseSize = 26.dp)
    PulseRing(progress = p2, maxScale = 2.1f, baseSize = 26.dp)
}

@Composable
private fun PulseRing(progress: Float, maxScale: Float, baseSize: Dp) {
    val scale = 1f + (maxScale - 1f) * progress
    val alpha = (1f - progress).coerceIn(0f, 1f) * 0.45f
    Box(
        modifier = Modifier
            .size(baseSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .background(
                Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to ActiveRose.copy(alpha = 0.35f),
                        0.55f to ActiveRose.copy(alpha = 0.12f),
                        1.0f to Color.Transparent,
                    ),
                ),
                CircleShape,
            ),
    )
}

/**
 * 三颗轨道光点：叠在图标之上（避免被底盘挡住）。
 * 从中心沿 -45°/45°/135° 直线飞出 11dp，渐显→放大→渐隐，错峰 0.7s。
 */
@Composable
private fun UnreadSparkleBurst(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "unread-sparkle")
    val clock by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(SparkleCycleMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sparkle-clock",
    )
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        SparkleAngles.forEachIndexed { index, angleDeg ->
            key(index) {
                SparkleDot(clock = clock, index = index, angleDeg = angleDeg)
            }
        }
    }
}

@Composable
private fun SparkleDot(clock: Float, index: Int, angleDeg: Float) {
    val tMs = clock * SparkleCycleMs
    val local = (tMs - index * SparkleStaggerMs + SparkleCycleMs) % SparkleCycleMs
    val active = local <= SparkleDurationMs
    val p = if (active) (local / SparkleDurationMs).coerceIn(0f, 1f) else 0f
    val appear = (p / 0.15f).coerceIn(0f, 1f)
    val fade = ((1f - p) / 0.25f).coerceIn(0f, 1f)
    val alpha = if (active) appear * fade else 0f
    val distDp = 11f * p
    val scale = 0.4f + 0.85f * p
    val rad = Math.toRadians(angleDeg.toDouble())
    val xDp = (cos(rad) * distDp).toFloat()
    val yDp = (sin(rad) * distDp).toFloat()
    Box(
        modifier = Modifier
            .graphicsLayer {
                translationX = xDp.dp.toPx()
                translationY = yDp.dp.toPx()
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .size(3.5.dp)
            .background(ActiveRose, CircleShape),
    )
}

@Composable
private fun UnreadAnimatedBody(count: Int, badgeScale: Float) {
    val density = LocalDensity.current
    val infinite = rememberInfiniteTransition(label = "unread-bounce")
    val yDp by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = BounceCycleMs
                0f at 0
                -4f at 350 using FastOutSlowInEasing
                0f at 700 using FastOutSlowInEasing
                -2f at 1050 using FastOutSlowInEasing
                0f at 1400 using FastOutSlowInEasing
                0f at BounceActiveMs
                0f at BounceCycleMs
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "bounce-y",
    )
    val rot by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = BounceCycleMs
                0f at 0
                -6f at 450 using FastOutSlowInEasing
                6f at 1100 using FastOutSlowInEasing
                -3f at 1700 using FastOutSlowInEasing
                0f at 2300 using FastOutSlowInEasing
                0f at BounceActiveMs
                0f at BounceCycleMs
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "bounce-rot",
    )
    val bounceY = with(density) { yDp.dp.toPx() }
    UnreadIconCore(
        lit = true,
        count = count,
        badgeScale = badgeScale,
        translationY = bounceY,
        rotationZ = rot,
    )
}

@Composable
private fun UnreadStaticBody(count: Int, badgeScale: Float) {
    UnreadIconCore(
        lit = false,
        count = count,
        badgeScale = badgeScale,
        translationY = 0f,
        rotationZ = 0f,
    )
}

@Composable
private fun UnreadIconCore(
    lit: Boolean,
    count: Int,
    badgeScale: Float,
    translationY: Float,
    rotationZ: Float,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .graphicsLayer {
                this.translationY = translationY
                this.rotationZ = rotationZ
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .then(
                    if (lit) {
                        Modifier
                            .background(
                                Brush.linearGradient(listOf(IconBgTop, IconBgBottom)),
                                CircleShape,
                            )
                            .border(0.8.dp, ActiveRose.copy(alpha = 0.35f), CircleShape)
                    } else {
                        Modifier.border(1.2.dp, QuietStroke, CircleShape)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            HeartWithSparkle(lit = lit)
        }

        // 光点叠在底盘之上、角标之下，才能看见飞散
        if (lit) {
            UnreadSparkleBurst(modifier = Modifier.fillMaxSize())
        }

        if (badgeScale > 0.01f && count > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-1).dp)
                    .graphicsLayer {
                        scaleX = badgeScale
                        scaleY = badgeScale
                        alpha = badgeScale
                    }
                    .shadow(
                        elevation = 3.dp,
                        shape = RoundedCornerShape(8.dp),
                        ambientColor = BadgeStart.copy(alpha = 0.35f),
                        spotColor = BadgeStart.copy(alpha = 0.4f),
                    )
                    .defaultMinSize(minWidth = 16.dp, minHeight = 14.dp)
                    .background(
                        Brush.linearGradient(listOf(BadgeStart, BadgeEnd)),
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (count > 9) "9+" else count.toString(),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun HeartWithSparkle(lit: Boolean) {
    val stroke = if (lit) ActiveRose else QuietStroke
    val fill = if (lit) ActiveRose.copy(alpha = 0.18f) else Color.Transparent
    Canvas(modifier = Modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val sx = w / 24f
        val sy = h / 24f
        val heart = Path().apply {
            moveTo(12f * sx, 20.5f * sy)
            cubicTo(12f * sx, 20.5f * sy, 3f * sx, 14.5f * sy, 3f * sx, 8.5f * sy)
            cubicTo(3f * sx, 5.42f * sy, 5.42f * sx, 3f * sy, 8.5f * sx, 3f * sy)
            cubicTo(10.24f * sx, 3f * sy, 11.91f * sx, 3.81f * sy, 13f * sx, 5.08f * sy)
            cubicTo(14.09f * sx, 3.81f * sy, 15.76f * sx, 3f * sy, 17.5f * sx, 3f * sy)
            cubicTo(20.58f * sx, 3f * sy, 23f * sx, 5.42f * sy, 23f * sx, 8.5f * sy)
            cubicTo(23f * sx, 14.5f * sy, 14f * sx, 20.5f * sy, 12f * sx, 20.5f * sy)
            close()
        }
        if (lit) {
            drawPath(heart, color = fill)
        }
        drawPath(
            path = heart,
            color = stroke,
            style = Stroke(width = 1.5f * sx, join = StrokeJoin.Round, cap = StrokeCap.Round),
        )
        val cx = 12f * sx
        val cy = 11f * sy
        val sparkle = if (lit) ActiveRose.copy(alpha = 0.75f) else QuietStroke.copy(alpha = 0.55f)
        drawCircle(color = sparkle, radius = 1.35f * sx, center = Offset(cx, cy))
        val arm = 2.2f * sx
        val sw = 1f * sx
        drawLine(sparkle.copy(alpha = 0.55f), Offset(cx, cy - arm), Offset(cx, cy - arm * 0.35f), sw, StrokeCap.Round)
        drawLine(sparkle.copy(alpha = 0.55f), Offset(cx, cy + arm * 0.35f), Offset(cx, cy + arm), sw, StrokeCap.Round)
        drawLine(sparkle.copy(alpha = 0.55f), Offset(cx - arm, cy), Offset(cx - arm * 0.35f, cy), sw, StrokeCap.Round)
        drawLine(sparkle.copy(alpha = 0.55f), Offset(cx + arm * 0.35f, cy), Offset(cx + arm, cy), sw, StrokeCap.Round)
    }
}
