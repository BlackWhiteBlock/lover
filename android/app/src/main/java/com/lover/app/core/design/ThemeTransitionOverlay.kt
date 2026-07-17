package com.lover.app.core.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lover.app.R
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class RevealPhase { Approach, Burst, Fill, Done }

private data class HeartParticle(
    val angleDeg: Float,
    val delayMs: Long,
    val distDp: Float,
    val sizeDp: Float,
    val color: Color,
)

private val Particles = listOf(
    HeartParticle(-70f, 0, 160f, 14f, Color(0xFFFB7185)),
    HeartParticle(-20f, 50, 200f, 10f, Color(0xFFFDA4AF)),
    HeartParticle(20f, 20, 190f, 16f, Color(0xFFFB7185)),
    HeartParticle(70f, 80, 150f, 11f, Color(0xFFF43F5E)),
    HeartParticle(110f, 40, 180f, 13f, Color(0xFFFDA4AF)),
    HeartParticle(160f, 70, 170f, 9f, Color(0xFFFB7185)),
    HeartParticle(-120f, 30, 185f, 15f, Color(0xFFF43F5E)),
    HeartParticle(-160f, 60, 155f, 10f, Color(0xFFFDA4AF)),
    HeartParticle(-45f, 90, 210f, 12f, Color(0xFFFB7185)),
    HeartParticle(45f, 10, 195f, 8f, Color(0xFFFDA4AF)),
    HeartParticle(135f, 50, 165f, 14f, Color(0xFFF43F5E)),
    HeartParticle(-135f, 30, 175f, 11f, Color(0xFFFB7185)),
)

private const val ImpactMs = 900L
private val ApproachEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private val FillEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val BounceEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/**
 * Solo → Couple cinematic reveal (~3.3s), matching ThemeTransitionOverlay.tsx.
 */
@Composable
fun ThemeTransitionOverlay(
    visible: Boolean,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    var phase by remember { mutableStateOf(RevealPhase.Approach) }
    val overlayAlpha = remember { Animatable(1f) }
    val leftProgress = remember { Animatable(0f) }
    val rightProgress = remember { Animatable(0f) }
    val heartScale = remember { Animatable(0.8f) }
    val heartAlpha = remember { Animatable(0f) }
    val fillProgress = remember { Animatable(0f) }
    val glowProgress = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0f) }
    val logoAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val textOffset = remember { Animatable(16f) }
    val particleProgress = remember { Animatable(0f) }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        phase = RevealPhase.Approach
        overlayAlpha.snapTo(1f)
        leftProgress.snapTo(0f)
        rightProgress.snapTo(0f)
        heartScale.snapTo(0.8f)
        heartAlpha.snapTo(0f)
        fillProgress.snapTo(0f)
        glowProgress.snapTo(0f)
        logoScale.snapTo(0f)
        logoAlpha.snapTo(0f)
        textAlpha.snapTo(0f)
        textOffset.snapTo(16f)
        particleProgress.snapTo(0f)

        // Approach: fly in
        launch {
            heartAlpha.animateTo(1f, tween(300))
        }
        launch {
            leftProgress.animateTo(1f, tween(ImpactMs.toInt(), easing = ApproachEasing))
        }
        launch {
            delay(50)
            rightProgress.animateTo(1f, tween(ImpactMs.toInt(), easing = ApproachEasing))
        }
        heartScale.animateTo(1f, tween(ImpactMs.toInt(), easing = ApproachEasing))

        // Burst
        phase = RevealPhase.Burst
        launch {
            heartScale.animateTo(1.4f, tween(250, easing = LinearOutSlowInEasing))
        }
        launch {
            glowProgress.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        }
        launch {
            particleProgress.animateTo(1f, tween(1200, easing = CubicBezierEasing(0.2f, 0.8f, 0.4f, 1f)))
        }

        delay(150) // t ≈ 1050ms → fill
        phase = RevealPhase.Fill
        launch {
            fillProgress.animateTo(1f, tween(900, easing = FillEasing))
        }
        launch {
            heartAlpha.animateTo(0f, tween(250))
            heartScale.animateTo(0.3f, tween(250))
        }
        launch {
            logoAlpha.animateTo(1f, tween(400))
            logoScale.animateTo(1f, tween(500, easing = BounceEasing))
        }
        launch {
            delay(300)
            textAlpha.animateTo(1f, tween(600))
            textOffset.animateTo(0f, tween(600, easing = FastOutSlowInEasing))
        }

        delay(1450) // t ≈ 2500ms → start curtain fade
        phase = RevealPhase.Done
        delay(100)
        overlayAlpha.animateTo(0f, tween(800, easing = FastOutSlowInEasing))
        onComplete()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha.value }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val coverDiameter = hypot(widthPx, heightPx) * 1.15f
        val coverScale = (coverDiameter / with(density) { 80.dp.toPx() }).coerceAtLeast(1f)

        // Rose ink fill
        Box(
            modifier = Modifier
                .size(80.dp)
                .scale(fillProgress.value * coverScale)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFF0F2),
                            Color(0xFFFFE4E8),
                            Color(0xFFFECDD3),
                            Color(0xFFFB7185),
                        ),
                    ),
                    shape = CircleShape,
                )
                .graphicsLayer { alpha = fillProgress.value.coerceIn(0f, 1f) },
        )

        // Impact glow
        if (phase == RevealPhase.Burst || phase == RevealPhase.Fill) {
            val glowScale = when {
                glowProgress.value < 0.35f -> glowProgress.value / 0.35f * 2.8f
                else -> 2.8f - (glowProgress.value - 0.35f) / 0.65f * 1.2f
            }
            val glowAlpha = when {
                glowProgress.value < 0.2f -> glowProgress.value / 0.2f
                else -> (1f - (glowProgress.value - 0.2f) / 0.8f).coerceIn(0f, 1f)
            }
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(glowScale.coerceAtLeast(0f))
                    .blur(8.dp)
                    .background(
                        brush = Brush.radialGradient(
                            listOf(
                                Color.White.copy(alpha = 0.95f),
                                Color(0xFFFB7185).copy(alpha = 0.6f),
                                Color.Transparent,
                            ),
                        ),
                        shape = CircleShape,
                    )
                    .graphicsLayer { alpha = glowAlpha },
            )
        }

        // Flying hearts
        val startX = widthPx * 0.48f
        val leftX = -startX + leftProgress.value * (startX - with(density) { 32.dp.toPx() })
        val rightX = startX - rightProgress.value * (startX - with(density) { 32.dp.toPx() })
        Icon(
            painter = painterResource(R.drawable.ic_heart_fill),
            contentDescription = null,
            tint = Color(0xFFFB7185),
            modifier = Modifier
                .offset { IntOffset(leftX.roundToInt(), 0) }
                .size(52.dp)
                .scale(heartScale.value)
                .graphicsLayer { alpha = heartAlpha.value },
        )
        Icon(
            painter = painterResource(R.drawable.ic_heart_fill),
            contentDescription = null,
            tint = Color(0xFFFB7185),
            modifier = Modifier
                .offset { IntOffset(rightX.roundToInt(), 0) }
                .size(52.dp)
                .scale(heartScale.value)
                .graphicsLayer { alpha = heartAlpha.value },
        )

        // Particles
        if (phase == RevealPhase.Burst || phase == RevealPhase.Fill || phase == RevealPhase.Done) {
            Particles.forEach { p ->
                val localT = ((particleProgress.value * 1200f - p.delayMs) / 1200f).coerceIn(0f, 1f)
                if (localT <= 0f) return@forEach
                val rad = Math.toRadians(p.angleDeg.toDouble())
                val distPx = with(density) { p.distDp.dp.toPx() } * localT
                val tx = (cos(rad) * distPx).toFloat()
                val ty = (sin(rad) * distPx).toFloat()
                val pAlpha = when {
                    localT < 0.15f -> localT / 0.15f
                    localT > 0.7f -> (1f - localT) / 0.3f
                    else -> 1f
                }.coerceIn(0f, 1f)
                val pScale = when {
                    localT < 0.2f -> localT / 0.2f * 1.2f
                    else -> 1.2f - (localT - 0.2f) * 1f
                }.coerceAtLeast(0.2f)
                Icon(
                    painter = painterResource(R.drawable.ic_heart_fill),
                    contentDescription = null,
                    tint = p.color,
                    modifier = Modifier
                        .offset { IntOffset(tx.roundToInt(), ty.roundToInt()) }
                        .size(p.sizeDp.dp)
                        .scale(pScale)
                        .graphicsLayer {
                            alpha = pAlpha
                            rotationZ = p.angleDeg + 30f * localT
                        },
                )
            }
        }

        // Logo + welcome copy
        if (phase == RevealPhase.Fill || phase == RevealPhase.Done) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = logoAlpha.value
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                    },
            ) {
                LoverLogo(size = 72.dp)
                Spacer(modifier = Modifier.height(18.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(horizontal = 28.dp)
                        .graphicsLayer {
                            alpha = textAlpha.value
                            translationY = textOffset.value
                        },
                ) {
                    Text(
                        text = "欢迎你来到“我们”的空间 ♡",
                        color = Color(0xFFBE185D).copy(alpha = 0.88f),
                        fontSize = 22.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        lineHeight = 30.sp,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "YOU FOUND EACH OTHER",
                        color = Color(0xFFF43F5E).copy(alpha = 0.55f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 3.sp,
                    )
                }
            }
        }
    }
}
