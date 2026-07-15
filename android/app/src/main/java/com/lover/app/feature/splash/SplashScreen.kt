package com.lover.app.feature.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lover.app.R
import com.lover.app.core.design.LoverAppName
import com.lover.app.core.design.WarmBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * SplashScreen.tsx:
 * - Two identical petals share tip origin (50% / 87.5%)
 * - Approach on X, then rotate to ±15° → logo silhouette
 * - Wordmark appears below the joined mark
 */
private const val ApproachDelayMs = 700L
private const val ApproachDurMs = 2200L
private const val ImpactMs = ApproachDelayMs + ApproachDurMs
private const val TotalMs = 6800L

private val SvgSize = 148.dp
/** Distance from merged heart tip cluster to the "lover." wordmark */
private val StageGap = 16.dp

private val EaseOutExpo = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val EaseOutBack = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
private val EaseApproach = CubicBezierEasing(0.33f, 0f, 0.2f, 1f)

private val TaglineColor = Color(0xFFD6D3D1)
private val RoseWash = Color(0xFFFFE8EC)
private val RoseMid = Color(0xFFFFF4F5)

/** Tip in SVG / viewBox — same as TIP_ORIGIN = 50% 87.5% */
private val TipOrigin = TransformOrigin(0.5f, 0.875f)

@Composable
fun SplashScreen(onComplete: () -> Unit) {
    val density = LocalDensity.current
    val svgSizePx = with(density) { SvgSize.toPx() }
    val stageHeightPx = svgSizePx * (96f / 100f)

    val leftX = remember { Animatable(-svgSizePx * 0.95f) }
    val rightX = remember { Animatable(svgSizePx * 0.95f) }
    val leftOpacity = remember { Animatable(0f) }
    val rightOpacity = remember { Animatable(0f) }
    val leftRotate = remember { Animatable(0f) }
    val rightRotate = remember { Animatable(0f) }
    val leftScale = remember { Animatable(1f) }
    val rightScale = remember { Animatable(1f) }

    val ripple1 = remember { Animatable(0f) }
    val ripple2 = remember { Animatable(0f) }
    val ripple3 = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }

    val wordOpacity = remember { Animatable(0f) }
    val wordY = remember { Animatable(14f) }
    val rootOpacity = remember { Animatable(1f) }

    val washPulse by rememberInfiniteTransition(label = "wash").animateFloat(
        initialValue = 0.94f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "washScale",
    )

    LaunchedEffect(Unit) {
        launch {
            delay(ApproachDelayMs - 100)
            leftOpacity.animateTo(1f, tween(400))
        }
        launch {
            delay(ApproachDelayMs - 60)
            rightOpacity.animateTo(1f, tween(400))
        }
        launch {
            delay(ApproachDelayMs)
            leftX.animateTo(0f, tween(ApproachDurMs.toInt(), easing = EaseApproach))
            leftX.snapTo(0f)
        }
        launch {
            delay(ApproachDelayMs + 40)
            rightX.animateTo(0f, tween(ApproachDurMs.toInt(), easing = EaseApproach))
            rightX.snapTo(0f)
        }

        launch {
            delay(ImpactMs)
            leftRotate.animateTo(15f, tween(700, easing = EaseOutBack))
            leftRotate.snapTo(15f)
        }
        launch {
            delay(ImpactMs)
            rightRotate.animateTo(-15f, tween(700, easing = EaseOutBack))
            rightRotate.snapTo(-15f)
        }
        launch {
            delay(ImpactMs)
            leftScale.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = 900
                    1f at 0
                    1.22f at 200 using LinearEasing
                    0.87f at 480 using LinearEasing
                    1.06f at 700 using LinearEasing
                    1f at 900 using EaseOutExpo
                },
            )
            leftScale.snapTo(1f)
        }
        launch {
            delay(ImpactMs + 40)
            rightScale.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = 900
                    1f at 0
                    1.22f at 200 using LinearEasing
                    0.87f at 480 using LinearEasing
                    1.06f at 700 using LinearEasing
                    1f at 900 using EaseOutExpo
                },
            )
            rightScale.snapTo(1f)
        }

        launch {
            delay(ImpactMs)
            ripple1.animateTo(1f, tween(1500, easing = EaseOutExpo))
        }
        launch {
            delay(ImpactMs + 140)
            ripple2.animateTo(1f, tween(1500, easing = EaseOutExpo))
        }
        launch {
            delay(ImpactMs + 300)
            ripple3.animateTo(1f, tween(1500, easing = EaseOutExpo))
        }
        launch {
            delay(ImpactMs)
            glow.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        }

        launch {
            delay(ImpactMs + 650)
            launch { wordOpacity.animateTo(1f, tween(1000, easing = EaseOutExpo)) }
            launch { wordY.animateTo(0f, tween(1000, easing = EaseOutExpo)) }
        }

        launch {
            delay((TotalMs * 0.82f).toLong())
            rootOpacity.animateTo(0f, tween((TotalMs * 0.18f).toInt(), easing = FastOutSlowInEasing))
        }

        delay(TotalMs)
        onComplete()
    }

    // Column: [hearts stage] + gap + wordmark — tip is near bottom of stage.
    // Optical tip ≈ screenCenterY - (wordmarkBlock + gap) / 2
    val wordmarkBlockApprox = 72.dp
    val tipAboveCenter = (wordmarkBlockApprox + StageGap) / 2

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = rootOpacity.value },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val tip = Offset(
                x = size.width / 2f,
                y = size.height / 2f - tipAboveCenter.toPx(),
            )
            val radius = max(size.width, size.height) * 0.75f * washPulse
            drawRect(WarmBackground)
            drawRect(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to RoseWash.copy(alpha = 0.9f),
                        0.3f to RoseMid.copy(alpha = 0.5f),
                        0.58f to WarmBackground.copy(alpha = 0.25f),
                        0.85f to WarmBackground.copy(alpha = 0.06f),
                        1f to Color.Transparent,
                    ),
                    center = tip,
                    radius = radius,
                ),
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(StageGap),
        ) {
            // Hearts stage — both petals absolutely stacked, tip origin shared
            Box(
                Modifier.size(
                    width = SvgSize * 2,
                    height = with(density) { stageHeightPx.toDp() },
                ),
                contentAlignment = Alignment.TopCenter,
            ) {
                val glowT = glow.value
                if (glowT > 0f) {
                    val (gScale, gAlpha) = glowKeyframe(glowT)
                    Canvas(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .size(100.dp)
                            .graphicsLayer {
                                scaleX = gScale
                                scaleY = gScale
                                alpha = gAlpha
                            },
                    ) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFDA4AF).copy(alpha = 0.7f),
                                    Color(0xFFFDA4AF).copy(alpha = 0.15f),
                                    Color.Transparent,
                                ),
                            ),
                        )
                    }
                }

                RippleRing(
                    progress = ripple1.value,
                    maxScale = 2.8f,
                    color = Color(0xFFFB7185).copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                RippleRing(
                    progress = ripple2.value,
                    maxScale = 3.8f,
                    color = Color(0xFFFB7185).copy(alpha = 0.3f),
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                RippleRing(
                    progress = ripple3.value,
                    maxScale = 5.0f,
                    color = Color(0xFFFB7185).copy(alpha = 0.18f),
                    modifier = Modifier.align(Alignment.BottomCenter),
                )

                HeartPetal(
                    translationX = leftX.value,
                    rotationZ = leftRotate.value,
                    scale = leftScale.value,
                    alpha = leftOpacity.value,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                HeartPetal(
                    translationX = rightX.value,
                    rotationZ = rightRotate.value,
                    scale = rightScale.value,
                    alpha = rightOpacity.value,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    alpha = wordOpacity.value
                    translationY = wordY.value * density.density
                },
            ) {
                LoverAppName(
                    fontSize = 60.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "TWO HEARTS · ONE WORLD",
                    color = TaglineColor,
                    fontWeight = FontWeight.Light,
                    fontSize = 9.sp,
                    letterSpacing = 4.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun HeartPetal(
    translationX: Float,
    rotationZ: Float,
    scale: Float,
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(R.drawable.heart_petal),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = modifier
            .size(width = SvgSize, height = SvgSize * (96f / 100f))
            .graphicsLayer {
                this.translationX = translationX
                this.rotationZ = rotationZ
                this.scaleX = scale
                this.scaleY = scale
                this.alpha = alpha
                // Critical: tip-first origin — when translationX→0 both tips coincide,
                // then ±15° rotation recreates the logo.
                transformOrigin = TipOrigin
            },
    )
}

@Composable
private fun RippleRing(
    progress: Float,
    maxScale: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (progress <= 0f) return
    val scale = when {
        progress < 0.01f -> (progress / 0.01f) * 0.01f
        else -> {
            val t = ((progress - 0.01f) / 0.99f).coerceIn(0f, 1f)
            0.01f + (maxScale - 0.01f) * t
        }
    }
    val alpha = when {
        progress < 0.01f -> progress / 0.01f
        else -> 1f - ((progress - 0.01f) / 0.99f)
    }
    Canvas(
        modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha.coerceIn(0f, 1f)
            },
    ) {
        drawCircle(
            color = color,
            radius = size.minDimension / 2f,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
        )
    }
}

private fun glowKeyframe(t: Float): Pair<Float, Float> {
    fun lerp(a: Float, b: Float, u: Float) = a + (b - a) * u
    return when {
        t <= 0.01f -> 0f to 0f
        t <= 0.22f -> {
            val u = (t - 0.01f) / 0.21f
            lerp(0f, 1.4f, u) to lerp(0f, 0.55f, u)
        }
        t <= 0.55f -> {
            val u = (t - 0.22f) / 0.33f
            lerp(1.4f, 1.8f, u) to lerp(0.55f, 0.25f, u)
        }
        else -> {
            val u = (t - 0.55f) / 0.45f
            lerp(1.8f, 0f, u) to lerp(0.25f, 0f, u)
        }
    }
}
