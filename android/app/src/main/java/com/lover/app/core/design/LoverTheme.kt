package com.lover.app.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Rose = Color(0xFFE88998)
val DeepRose = Color(0xFFB84F66)
val WarmBackground = Color(0xFFFFFCFB)
val Blush = Color(0xFFFFE9E6)
val Peach = Color(0xFFFFDCC8)
val Stone = Color(0xFF857A78)

private val LoverColors = lightColorScheme(
    primary = Rose,
    onPrimary = Color.White,
    primaryContainer = Blush,
    onPrimaryContainer = DeepRose,
    secondary = Color(0xFFEFA77E),
    background = WarmBackground,
    surface = Color.White,
    surfaceVariant = Color(0xFFFFF4F1),
    onSurface = Color(0xFF332927),
    onSurfaceVariant = Stone,
    error = Color(0xFFB3261E),
)

private val LoverTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 46.sp,
        color = DeepRose,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
    ),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp),
)

@Composable
fun LoverTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LoverColors,
        typography = LoverTypography,
        shapes = Shapes(
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(24.dp),
            large = RoundedCornerShape(36.dp),
        ),
        content = content,
    )
}
