package com.lover.app.core.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lover.app.R

/** App name wordmark — always "lover."; color follows mood. */
@Composable
fun LoverAppName(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 56.sp,
    textAlign: TextAlign? = null,
) {
    val mood = LocalMood.current
    Text(
        text = "lover.",
        modifier = modifier,
        style = loverAppNameStyle(fontSize).copy(color = mood.appNameColor),
        textAlign = textAlign,
    )
}

/**
 * Brand mark rendered from the SVG-derived vector (`lover_logo`).
 * In solo mood, tinted dusty purple.
 */
@Composable
fun LoverLogo(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val mood = LocalMood.current
    Image(
        painter = painterResource(R.drawable.lover_logo),
        contentDescription = mood.appName.trimEnd('.'),
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
        colorFilter = if (mood.solo) ColorFilter.tint(mood.logoTint) else null,
    )
}

/** Soft-edged PNG mark for larger hero placements when desired. */
@Composable
fun LoverLogoPhoto(
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
) {
    val mood = LocalMood.current
    Image(
        painter = painterResource(R.drawable.lover_logo_full),
        contentDescription = mood.appName.trimEnd('.'),
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
        colorFilter = if (mood.solo) ColorFilter.tint(mood.logoTint) else null,
    )
}

@Composable
fun LoverWordmark(
    modifier: Modifier = Modifier,
    showTagline: Boolean = true,
    logoSize: Dp = 72.dp,
    usePhoto: Boolean = true,
) {
    val mood = LocalMood.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (usePhoto) {
            LoverLogoPhoto(size = logoSize)
        } else {
            LoverLogo(size = logoSize)
        }
        LoverAppName(fontSize = 56.sp)
        if (showTagline) {
            Text(
                mood.tagline,
                style = MaterialTheme.typography.labelMedium,
                color = mood.stone,
            )
        }
    }
}

@Composable
fun LoverBrandRow(
    modifier: Modifier = Modifier,
    logoSize: Dp = 40.dp,
) {
    val mood = LocalMood.current
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LoverLogo(size = logoSize)
            LoverAppName(fontSize = 48.sp)
        }
        Text(
            mood.tagline,
            style = MaterialTheme.typography.labelSmall,
            color = mood.stone,
        )
    }
}
