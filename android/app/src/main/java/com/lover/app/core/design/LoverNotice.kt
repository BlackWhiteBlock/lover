package com.lover.app.core.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lover.app.core.notice.AppNotice
import com.lover.app.core.notice.NoticeKind
import com.lover.app.core.notice.NoticeStore
import kotlinx.coroutines.delay

@Composable
fun LoverNoticeHost(
    noticeStore: NoticeStore,
    modifier: Modifier = Modifier,
    autoDismissMs: Long = 3600,
) {
    val notice by noticeStore.notice.collectAsStateWithLifecycle()
    LoverTopNotice(
        notice = notice,
        onDismiss = noticeStore::clear,
        modifier = modifier,
        autoDismissMs = autoDismissMs,
    )
}

@Composable
fun LoverTopNotice(
    notice: AppNotice?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    autoDismissMs: Long = 2800,
) {
    LaunchedEffect(notice?.id) {
        if (notice == null) return@LaunchedEffect
        delay(autoDismissMs)
        onDismiss()
    }

    Box(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = notice != null,
            enter = fadeIn(tween(220)) + slideInVertically(tween(280)) { -it },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { -it / 2 },
        ) {
            val current = notice ?: return@AnimatedVisibility
            TopNoticeCard(current)
        }
    }
}

@Composable
private fun TopNoticeCard(notice: AppNotice) {
    val icon = when (notice.kind) {
        NoticeKind.Success -> Icons.Rounded.CheckCircle
        NoticeKind.Error -> Icons.Rounded.ErrorOutline
        NoticeKind.Info -> Icons.Rounded.Info
    }
    val accent = when (notice.kind) {
        NoticeKind.Success -> DeepRose
        NoticeKind.Error -> Color(0xFFB4535A)
        NoticeKind.Info -> Stone
    }
    val wash = when (notice.kind) {
        NoticeKind.Success -> Blush
        NoticeKind.Error -> Color(0xFFFFF0F0)
        NoticeKind.Info -> SoftSurface
    }
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(22.dp), clip = false)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color.White.copy(alpha = 0.98f), wash.copy(alpha = 0.92f)),
                ),
            )
            .border(1.dp, SoftOutline.copy(alpha = 0.7f), RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .background(accent.copy(alpha = 0.12f), CircleShape)
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = accent)
        }
        Text(
            notice.message,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (notice.kind == NoticeKind.Success) DeepRose else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
