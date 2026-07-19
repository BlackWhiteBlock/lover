package com.lover.app.feature.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lover.app.core.design.LocalMood
import com.lover.app.core.model.Letter
import com.lover.app.core.model.LetterDeliveryStatus
import com.lover.app.core.model.LetterType
import kotlinx.coroutines.delay

/**
 * 信封详情全屏页。
 * [playOpenAnimation]=true 时先播放拆信动效，再展示正文。
 */
@Composable
fun LetterDetailScreen(
    letter: Letter,
    currentUserId: String?,
    playOpenAnimation: Boolean,
    onClose: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val mood = LocalMood.current
    val unlocked = letter.isUnlocked
    val isAuthor = currentUserId != null && currentUserId == letter.senderId
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var showContent by remember { mutableStateOf(!playOpenAnimation || !unlocked) }

    val flap = remember { Animatable(0f) }
    val lift = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(if (playOpenAnimation && unlocked) 0f else 1f) }

    LaunchedEffect(letter.id, playOpenAnimation) {
        if (playOpenAnimation && unlocked) {
            showContent = false
            flap.snapTo(0f)
            lift.snapTo(0f)
            contentAlpha.snapTo(0f)
            // 信封轻抬
            lift.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            // 封口掀开
            flap.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
            delay(180)
            showContent = true
            contentAlpha.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
        } else {
            showContent = true
            contentAlpha.snapTo(1f)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = mood.background,
            title = { Text("确认删除") },
            text = { Text("删除后无法恢复，确定要删除这封信吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete?.invoke()
                        onClose()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFC45C5C)),
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDelete = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = mood.stone),
                ) { Text("取消") }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(mood.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回", tint = mood.accent)
            }
            Spacer(modifier = Modifier.weight(1f))
            if (isAuthor && onDelete != null) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "删除", tint = Color(0xFFC45C5C))
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            // 拆信动效层
            if (playOpenAnimation && unlocked && contentAlpha.value < 0.95f) {
                Column(
                    modifier = Modifier
                        .padding(top = 48.dp)
                        .graphicsLayer {
                            translationY = -12f * lift.value
                            scaleX = 0.96f + 0.04f * lift.value
                            scaleY = 0.96f + 0.04f * lift.value
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 200.dp, height = 140.dp)
                            .graphicsLayer {
                                // 封口掀起：上移 + 略旋 + 渐隐
                                translationY = -36f * flap.value
                                rotationZ = -8f * flap.value
                                alpha = 1f - 0.85f * flap.value
                                scaleY = 1f - 0.25f * flap.value
                            }
                            .background(
                                mood.soft.copy(alpha = 0.35f),
                                RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = mood.soft,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(width = 200.dp, height = 120.dp)
                            .background(
                                Color.White.copy(alpha = 0.96f),
                                RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Mail,
                            contentDescription = null,
                            tint = mood.soft.copy(alpha = 0.55f),
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        "正在拆开信件…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = mood.stone,
                    )
                }
            }

            if (showContent) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = contentAlpha.value }
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 40.dp),
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            if (letter.type == LetterType.INSTANT) "Instant" else "Time Capsule",
                            style = MaterialTheme.typography.labelSmall,
                            color = mood.soft,
                        )
                        if (isAuthor && letter.deliveryStatus != null) {
                            Text(
                                text = when (letter.deliveryStatus) {
                                    LetterDeliveryStatus.READ -> "已阅"
                                    LetterDeliveryStatus.SENT -> "已寄"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (letter.deliveryStatus == LetterDeliveryStatus.READ) {
                                    mood.soft
                                } else {
                                    mood.stone
                                },
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        letter.title,
                        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Serif),
                        color = mood.accent,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "${letter.senderNickname} · ${letter.createdAt.take(10)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = mood.stone,
                    )
                    Spacer(modifier = Modifier.height(28.dp))

                    if (unlocked) {
                        Text(
                            letter.content.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = FontFamily.Serif,
                                lineHeight = 28.sp,
                            ),
                            color = mood.accent.copy(alpha = 0.88f),
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = mood.stone.copy(alpha = 0.45f),
                                modifier = Modifier.size(36.dp),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                letterCapsuleLockDetail(letter),
                                style = MaterialTheme.typography.bodyMedium,
                                color = mood.stone,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}
