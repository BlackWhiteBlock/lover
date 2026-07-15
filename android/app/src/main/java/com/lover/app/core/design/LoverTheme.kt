package com.lover.app.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lover.app.R

val Rose = Color(0xFFE88998)
val DeepRose = Color(0xFFB84F66)
val WarmBackground = Color(0xFFFFFCFB)
val Blush = Color(0xFFFFE9E6)
val Peach = Color(0xFFFFDCC8)
val Stone = Color(0xFF857A78)
val SoftOutline = Color(0xFFE9DAD6)
val SoftSurface = Color(0xFFFFF7F5)

/** Google Fonts — Pinyon Script; only for the "lover." app name. */
val PinyonScript = FontFamily(Font(R.font.pinyon_script_regular))

/** rose-900 @ 50% — brand wordmark color */
val LoverAppNameColor = Color(0xFF9F1239).copy(alpha = 0.5f)

fun loverAppNameStyle(fontSize: TextUnit = 56.sp): TextStyle = TextStyle(
    fontFamily = PinyonScript,
    fontWeight = FontWeight.Normal,
    fontSize = fontSize,
    letterSpacing = 0.sp,
    color = LoverAppNameColor,
)

private val LoverColors = lightColorScheme(
    primary = Rose,
    onPrimary = Color.White,
    primaryContainer = Blush,
    onPrimaryContainer = DeepRose,
    secondary = Color(0xFFEFA77E),
    onSecondary = Color.White,
    secondaryContainer = Peach.copy(alpha = 0.55f),
    onSecondaryContainer = DeepRose,
    background = WarmBackground,
    surface = Color.White,
    surfaceVariant = SoftSurface,
    onSurface = Color(0xFF332927),
    onSurfaceVariant = Stone,
    outline = SoftOutline,
    outlineVariant = SoftOutline.copy(alpha = 0.7f),
    error = Color(0xFFC45C5C),
)

private val LoverTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 46.sp,
        color = DeepRose,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        color = Color(0xFF3A2C2A),
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontSize = 22.sp,
        color = Color(0xFF3A2C2A),
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = Stone,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
        color = Stone,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 1.4.sp,
        color = Stone,
    ),
)

@Composable
fun LoverTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LoverColors,
        typography = LoverTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(14.dp),
            small = RoundedCornerShape(18.dp),
            medium = RoundedCornerShape(26.dp),
            large = RoundedCornerShape(34.dp),
            extraLarge = RoundedCornerShape(40.dp),
        ),
        content = content,
    )
}
