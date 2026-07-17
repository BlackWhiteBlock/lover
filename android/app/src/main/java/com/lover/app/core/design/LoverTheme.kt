package com.lover.app.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
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

/** Soleil (solo) — dusty purple + parchment diary. */
val DustPurple = Color(0xFF6B5B80)
val Parchment = Color(0xFFF2EDE6)
val SoleilMist = Color(0xFFB8A9C4)
val SoleilBlush = Color(0xFFE8E0D6)
val SoleilPeach = Color(0xFFD9CFC3)
val SoleilStone = Color(0xFF7A7168)
val SoleilOutline = Color(0xFFD8D0C6)
val SoleilSurface = Color(0xFFF7F3ED)

/** Google Fonts — Pinyon Script; only for the "lover." app name. */
val PinyonScript = FontFamily(Font(R.font.pinyon_script_regular))

val EBGaramond = FontFamily(
    Font(R.font.eb_garamond_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.eb_garamond_italic, FontWeight.Normal, FontStyle.Italic),
)

val Lato = FontFamily(
    Font(R.font.lato_light, FontWeight.Light),
    Font(R.font.lato_regular, FontWeight.Normal),
)

/** rose-900 @ 50% — brand wordmark color */
val LoverAppNameColor = Color(0xFF9F1239).copy(alpha = 0.5f)
val SoleilAppNameColor = DustPurple.copy(alpha = 0.72f)

fun loverAppNameStyle(fontSize: TextUnit = 56.sp): TextStyle = TextStyle(
    fontFamily = PinyonScript,
    fontWeight = FontWeight.Normal,
    fontSize = fontSize,
    letterSpacing = 0.sp,
    color = LoverAppNameColor,
)

fun soleilAppNameStyle(fontSize: TextUnit = 56.sp): TextStyle = TextStyle(
    fontFamily = EBGaramond,
    fontWeight = FontWeight.Normal,
    fontStyle = FontStyle.Italic,
    fontSize = fontSize,
    letterSpacing = 0.sp,
    color = SoleilAppNameColor,
)

@Immutable
data class MoodPalette(
    val solo: Boolean,
    val accent: Color,
    val soft: Color,
    val blush: Color,
    val peach: Color,
    val stone: Color,
    val background: Color,
    val softOutline: Color,
    val softSurface: Color,
    val appName: String,
    val tagline: String,
    val appNameColor: Color,
    val logoTint: Color,
)

val CoupleMood = MoodPalette(
    solo = false,
    accent = DeepRose,
    soft = Rose,
    blush = Blush,
    peach = Peach,
    stone = Stone,
    background = WarmBackground,
    softOutline = SoftOutline,
    softSurface = SoftSurface,
    appName = "lover.",
    tagline = "TWO HEARTS · ONE WORLD",
    appNameColor = LoverAppNameColor,
    logoTint = Color(0xFFFB7185).copy(alpha = 0.45f),
)

val SoleilMood = MoodPalette(
    solo = true,
    accent = DustPurple,
    soft = SoleilMist,
    blush = SoleilBlush,
    peach = SoleilPeach,
    stone = SoleilStone,
    background = Parchment,
    softOutline = SoleilOutline,
    softSurface = SoleilSurface,
    // Brand name stays Lover.; Soleil is the unlinked mood (palette / tagline).
    appName = "lover.",
    tagline = "One heart · One day · One step",
    appNameColor = SoleilAppNameColor,
    logoTint = DustPurple.copy(alpha = 0.55f),
)

val LocalMood = staticCompositionLocalOf { CoupleMood }

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

private val SoleilColors = lightColorScheme(
    primary = DustPurple,
    onPrimary = Color.White,
    primaryContainer = SoleilBlush,
    onPrimaryContainer = DustPurple,
    secondary = SoleilMist,
    onSecondary = Color.White,
    secondaryContainer = SoleilPeach.copy(alpha = 0.65f),
    onSecondaryContainer = DustPurple,
    background = Parchment,
    surface = SoleilSurface,
    surfaceVariant = Color(0xFFEFE8DF),
    onSurface = Color(0xFF3A342E),
    onSurfaceVariant = SoleilStone,
    outline = SoleilOutline,
    outlineVariant = SoleilOutline.copy(alpha = 0.7f),
    error = Color(0xFFB05A5A),
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
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 22.sp,
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

private val SoleilTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = EBGaramond,
        fontWeight = FontWeight.Normal,
        fontSize = 46.sp,
        color = DustPurple,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = EBGaramond,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        color = Color(0xFF3A342E),
    ),
    titleLarge = TextStyle(
        fontFamily = EBGaramond,
        fontSize = 22.sp,
        color = Color(0xFF3A342E),
    ),
    bodyLarge = TextStyle(
        fontFamily = Lato,
        fontWeight = FontWeight.Light,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Lato,
        fontWeight = FontWeight.Light,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Lato,
        fontWeight = FontWeight.Light,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = SoleilStone,
    ),
    labelMedium = TextStyle(
        fontFamily = Lato,
        fontWeight = FontWeight.Light,
        fontSize = 11.sp,
        letterSpacing = 1.4.sp,
        color = SoleilStone,
    ),
    labelSmall = TextStyle(
        fontFamily = Lato,
        fontWeight = FontWeight.Light,
        fontSize = 10.sp,
        letterSpacing = 1.6.sp,
        color = SoleilStone,
    ),
)

@Composable
fun LoverTheme(
    soloMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val mood = if (soloMode) SoleilMood else CoupleMood
    CompositionLocalProvider(LocalMood provides mood) {
        MaterialTheme(
            colorScheme = if (soloMode) SoleilColors else LoverColors,
            typography = if (soloMode) SoleilTypography else LoverTypography,
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
}
