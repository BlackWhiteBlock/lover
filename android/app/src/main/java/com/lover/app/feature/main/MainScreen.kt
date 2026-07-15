@file:OptIn(ExperimentalMaterial3Api::class)

package com.lover.app.feature.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.lover.app.core.design.*
import com.lover.app.core.model.*
import java.time.LocalDate

internal const val MaxMediaPick = 9

enum class Editor { MEDIA, ANNIVERSARY, LETTER }

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.data.collectAsState()
    val tab by viewModel.selectedTab.collectAsState()
    val message by viewModel.message.collectAsState()
    var editor by remember { mutableStateOf<Editor?>(null) }
    var mediaDetail by remember { mutableStateOf<MediaItem?>(null) }
    var letterDetail by remember { mutableStateOf<Letter?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val composing = editor != null

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = WarmBackground,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (!composing) {
                    LoverNavigation(tab, viewModel::selectTab)
                }
            },
            floatingActionButton = {
                if (!composing) {
                    val target = when (tab) {
                        MainTab.TIMELINE -> Editor.MEDIA
                        MainTab.ANNIVERSARY -> Editor.ANNIVERSARY
                        MainTab.LETTERS -> Editor.LETTER
                        else -> null
                    }
                    if (target != null) {
                        FloatingActionButton(
                            onClick = { editor = target },
                            containerColor = Rose,
                            shape = RoundedCornerShape(22.dp),
                            elevation = FloatingActionButtonDefaults.elevation(4.dp, 6.dp),
                        ) { Icon(Icons.Rounded.Add, "新增", tint = Color.White) }
                    }
                }
            },
        ) { padding ->
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val forward = targetState.ordinal >= initialState.ordinal
                    val distance = 48
                    val enter = fadeIn(
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                    ) + slideInHorizontally(
                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                        initialOffsetX = { if (forward) distance else -distance },
                    )
                    val exit = fadeOut(
                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                    ) + slideOutHorizontally(
                        animationSpec = tween(240, easing = FastOutSlowInEasing),
                        targetOffsetX = { if (forward) -distance else distance },
                    )
                    enter togetherWith exit using SizeTransform(clip = false)
                },
                label = "tab",
                modifier = Modifier.padding(padding),
            ) { current ->
                Box(Modifier.fillMaxSize().widthIn(max = 600.dp)) {
                    when (current) {
                        MainTab.HOME -> HomePage(
                            state,
                            onMedia = { mediaDetail = it },
                            onCapture = { editor = Editor.MEDIA },
                            onWrite = {
                                viewModel.selectTab(MainTab.LETTERS)
                                editor = Editor.LETTER
                            },
                        )
                        MainTab.TIMELINE -> TimelinePage(state.media, { mediaDetail = it }) {
                            editor = Editor.MEDIA
                        }
                        MainTab.ANNIVERSARY -> AnniversaryPage(state.anniversaries) {
                            editor = Editor.ANNIVERSARY
                        }
                        MainTab.LETTERS -> LettersPage(state.letters, { letterDetail = it }) {
                            editor = Editor.LETTER
                        }
                        MainTab.PROFILE -> ProfilePage(
                            state = state,
                            onLogout = viewModel::logout,
                            onRequestUnbinding = viewModel::requestUnbinding,
                            onConfirmUnbinding = viewModel::confirmUnbinding,
                            onCancelUnbinding = viewModel::cancelUnbinding,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = composing,
            enter = fadeIn(tween(280)) + slideInVertically(tween(320)) { it / 10 },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 12 },
        ) {
            val current = editor
            if (current != null) {
                ComposerHost(
                    editor = current,
                    initialMediaUris = emptyList(),
                    onDismiss = { editor = null },
                    onSaveMedia = { uris, caption, date ->
                        viewModel.addMedia(uris, caption, date)
                        editor = null
                    },
                    onSaveAnniversary = { title, date, type ->
                        viewModel.addAnniversary(title, date, type)
                        editor = null
                    },
                    onSaveLetter = { title, content, type, date ->
                        viewModel.addLetter(title, content, type, date)
                        editor = null
                    },
                )
            }
        }

        mediaDetail?.let { MediaDetail(it) { mediaDetail = null } }
        letterDetail?.let { LetterDetail(it) { letterDetail = null } }
    }
}

@Composable
private fun LoverNavigation(selected: MainTab, onSelect: (MainTab) -> Unit) {
    val tabs = listOf(
        Triple(MainTab.HOME, "空间", Icons.Rounded.Favorite),
        Triple(MainTab.TIMELINE, "时光", Icons.Rounded.PhotoLibrary),
        Triple(MainTab.ANNIVERSARY, "纪念", Icons.Rounded.Event),
        Triple(MainTab.LETTERS, "信封", Icons.Rounded.Mail),
        Triple(MainTab.PROFILE, "我们", Icons.Rounded.People),
    )
    Surface(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        shape = RoundedCornerShape(36.dp),
        shadowElevation = 8.dp,
        tonalElevation = 0.dp,
        color = Color.White.copy(alpha = .96f),
        border = BorderStroke(1.dp, SoftOutline.copy(alpha = 0.55f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 4.dp)) {
            tabs.forEach { (tab, label, icon) ->
                val scale by animateFloatAsState(
                    targetValue = if (selected == tab) 1.06f else 1f,
                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                    label = "icon",
                )
                NavigationBarItem(
                    selected = selected == tab,
                    onClick = { onSelect(tab) },
                    icon = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(icon, label, Modifier.scale(scale))
                            if (selected == tab) {
                                Box(
                                    Modifier
                                        .padding(top = 3.dp)
                                        .size(width = 12.dp, height = 3.dp)
                                        .background(Rose, RoundedCornerShape(2.dp)),
                                )
                            }
                        }
                    },
                    label = { Text(label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Rose,
                        selectedTextColor = Rose,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = Stone,
                        unselectedTextColor = Stone,
                    ),
                )
            }
        }
    }
}

@Composable
private fun PageHeader(title: String, subtitle: String, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(subtitle.uppercase(), style = MaterialTheme.typography.labelSmall, color = Stone)
        }
        action?.invoke()
    }
}

@Composable
private fun HomePage(
    state: AppState,
    onMedia: (MediaItem) -> Unit,
    onCapture: () -> Unit,
    onWrite: () -> Unit,
) {
    val days = state.lovingDays ?: 0
    LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Column(Modifier.padding(horizontal = 22.dp, vertical = 14.dp)) {
                Text("lover.", style = MaterialTheme.typography.displayLarge)
                Text("TWO HEARTS · ONE WORLD", style = MaterialTheme.typography.labelSmall, color = Stone)
            }
        }
        item {
            Card(
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(34.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(listOf(Blush, Peach.copy(alpha = .7f))),
                            RoundedCornerShape(34.dp),
                        )
                        .padding(30.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("Loving Journey", style = MaterialTheme.typography.labelMedium, color = DeepRose)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$days", style = MaterialTheme.typography.displayLarge)
                            Text(" 天", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp), color = Stone)
                        }
                        Text("每一天，都算数", color = Stone)
                    }
                    Icon(
                        Icons.Rounded.Favorite,
                        null,
                        tint = Rose.copy(alpha = .22f),
                        modifier = Modifier.align(Alignment.CenterEnd).size(96.dp),
                    )
                }
            }
        }
        item {
            Row(
                Modifier.padding(20.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                QuickAction("写情书", "WRITE LOVE", Icons.Rounded.Edit, Modifier.weight(1f), onWrite)
                QuickAction("存瞬间", "CAPTURE NOW", Icons.Rounded.CameraAlt, Modifier.weight(1f), onCapture)
            }
        }
        item {
            Text("近期掠影", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 22.dp))
            Text("RECENT MOMENTS", style = MaterialTheme.typography.labelSmall, color = Stone, modifier = Modifier.padding(horizontal = 22.dp))
            Spacer(Modifier.height(12.dp))
            if (state.media.isEmpty()) {
                EmptyHint("还没有影像，存下第一个瞬间吧", Icons.Rounded.PhotoCamera)
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.media.take(6), key = { it.id }) {
                        MediaImage(it, Modifier.size(150.dp, 185.dp).clickable { onMedia(it) })
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAction(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f)),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, SoftOutline.copy(alpha = 0.65f)),
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(48.dp).background(Blush, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = Rose)
            }
            Spacer(Modifier.height(14.dp))
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Stone)
        }
    }
}

@Composable
private fun TimelinePage(media: List<MediaItem>, onMedia: (MediaItem) -> Unit, onAdd: () -> Unit) {
    Column {
        PageHeader("相爱时光", "Visual Memories") {
            IconButton(onClick = onAdd) { Icon(Icons.Rounded.AddCircle, "新增", tint = Rose) }
        }
        if (media.isEmpty()) {
            EmptyHint("选择照片或视频，记录共同的故事", Icons.Rounded.PhotoLibrary)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(media, key = { it.id }) {
                    MediaImage(it, Modifier.aspectRatio(.8f).clickable { onMedia(it) })
                }
            }
        }
    }
}

@Composable
private fun MediaImage(item: MediaItem, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(26.dp)).background(Blush)) {
        AsyncImage(
            model = item.thumbnailUrl ?: item.url,
            contentDescription = item.caption,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .58f))))
                .padding(12.dp),
        ) {
            Text(
                item.caption.ifBlank { item.mediaDate },
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.type == MediaType.VIDEO) {
            Icon(
                Icons.Rounded.PlayCircle,
                "视频",
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(44.dp),
            )
        }
    }
}

@Composable
private fun AnniversaryPage(anniversaries: List<Anniversary>, onAdd: () -> Unit) {
    Column {
        PageHeader("爱的纪念日", "Eternal Dates") {
            IconButton(onClick = onAdd) { Icon(Icons.Rounded.AddCircle, "新增", tint = Rose) }
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (anniversaries.isEmpty()) item { EmptyHint("把重要的日子珍藏在这里", Icons.Rounded.Event) }
            items(anniversaries, key = { it.id }) { anniversary ->
                val countdown = anniversary.countdown
                Card(
                    colors = CardDefaults.cardColors(containerColor = SoftSurface),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(0.dp),
                    border = BorderStroke(1.dp, SoftOutline.copy(alpha = 0.7f)),
                ) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(64.dp).background(Blush, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Favorite, null, tint = Rose)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                            Text(anniversary.title, style = MaterialTheme.typography.titleLarge)
                            Text(anniversary.date, color = Stone)
                            SuggestionChip(
                                onClick = {},
                                label = { Text(if (anniversary.type == AnniversaryType.YEARLY) "年度纪念" else "里程碑") },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = Blush,
                                    labelColor = DeepRose,
                                ),
                                border = null,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                if (countdown?.reached == true) "✓" else countdown?.days?.toString() ?: "—",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Rose,
                            )
                            Text(if (countdown?.reached == true) "已达成" else "天", color = Stone)
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, Rose.copy(alpha = 0.35f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Rose),
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("添加新的纪念日")
                }
            }
        }
    }
}

@Composable
private fun LettersPage(letters: List<Letter>, onLetter: (Letter) -> Unit, onAdd: () -> Unit) {
    Column {
        PageHeader("爱的信封", "Secret Letters") {
            IconButton(onClick = onAdd) { Icon(Icons.Rounded.Edit, "写信", tint = Rose) }
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (letters.isEmpty()) item { EmptyHint("给 TA 写第一封信吧", Icons.Rounded.MarkEmailUnread) }
            items(letters, key = { it.id }) { letter ->
                val unlocked = letter.isUnlocked
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onLetter(letter) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (unlocked) Color.White.copy(alpha = 0.96f) else Blush.copy(alpha = 0.75f),
                    ),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(0.dp),
                    border = BorderStroke(1.dp, SoftOutline.copy(alpha = 0.7f)),
                ) {
                    Row(Modifier.padding(22.dp), verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier.size(44.dp).background(if (unlocked) SoftSurface else Color.White.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (unlocked) Icons.Rounded.Drafts else Icons.Rounded.Lock,
                                null,
                                tint = Rose,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(letter.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                Text(letter.createdAt.take(10), style = MaterialTheme.typography.labelSmall, color = Stone)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (unlocked) letter.summary ?: letter.content.orEmpty()
                                else "时间胶囊 · ${letter.unlockAt?.take(10)} 解锁",
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = Stone,
                            )
                            Text("来自 ${letter.senderNickname}", style = MaterialTheme.typography.labelSmall, color = Rose)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfilePage(
    state: AppState,
    onLogout: () -> Unit,
    onRequestUnbinding: (String?) -> Unit,
    onConfirmUnbinding: (String) -> Unit,
    onCancelUnbinding: (String) -> Unit,
) {
    var confirm by remember { mutableStateOf<String?>(null) }
    var reason by rememberSaveable { mutableStateOf("") }
    val partner = state.couple?.members?.firstOrNull { it.id != state.user?.id }
    val pending = state.couple?.pendingUnbinding
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        item {
            PageHeader("我们的小宇宙", "Our Private World")
            Card(
                colors = CardDefaults.cardColors(containerColor = Blush.copy(alpha = 0.85f)),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                elevation = CardDefaults.cardElevation(0.dp),
            ) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row {
                        Avatar(state.user?.nickname?.take(1) ?: "我")
                        Avatar(partner?.nickname?.take(1) ?: "TA", Modifier.offset(x = (-12).dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(state.couple?.name ?: "我们", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Established ${state.couple?.togetherDate?.replace('-', '.') ?: "—"}",
                        color = Stone,
                    )
                    state.couple?.inviteCode?.let {
                        Text("邀请码 $it", style = MaterialTheme.typography.labelSmall, color = Rose)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)); Text("空间设置", style = MaterialTheme.typography.titleLarge) }
        item { SettingRow(Icons.Rounded.Palette, "空间装扮", "暖玫瑰 · 默认主题") }
        item { SettingRow(Icons.Rounded.Lock, "隐私控制", "仅情侣双方可见") }
        item { SettingRow(Icons.Rounded.Info, "关于 Lover", "版本 1.0.0") }
        item {
            Spacer(Modifier.height(20.dp))
            if (pending == null) {
                OutlinedButton(
                    onClick = { confirm = "request" },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, SoftOutline),
                ) {
                    Text("申请解除情侣绑定")
                }
            } else if (pending.requestedBy == state.user?.id) {
                Text("解绑申请等待伴侣确认", color = Stone)
                OutlinedButton(
                    onClick = { confirm = "cancel" },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Text("取消解绑申请")
                }
            } else {
                Text("伴侣发起了解绑申请", color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = { confirm = "confirm" },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Rose),
                ) {
                    Text("确认解除绑定")
                }
                TextButton(onClick = { confirm = "cancel" }, modifier = Modifier.fillMaxWidth()) {
                    Text("拒绝并取消申请")
                }
            }
            TextButton(onClick = { confirm = "logout" }, modifier = Modifier.fillMaxWidth()) { Text("退出登录") }
        }
    }
    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            shape = RoundedCornerShape(28.dp),
            containerColor = WarmBackground,
            title = {
                Text(
                    when (action) {
                        "request" -> "申请解绑？"
                        "confirm" -> "确认双方解绑？"
                        "cancel" -> "取消解绑申请？"
                        else -> "确认退出？"
                    },
                )
            },
            text = {
                if (action == "request") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("申请提交后，需要伴侣确认才会正式解绑。", color = Stone)
                        SoftTextField(
                            value = reason,
                            onValueChange = { reason = it.take(300) },
                            label = "原因（可选）",
                            minLines = 2,
                            singleLine = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    Text(
                        if (action == "confirm") "确认后情侣空间将解散，请谨慎操作。"
                        else if (action == "logout") "退出后可再次使用手机号登录。"
                        else "该待处理申请将被取消。",
                        color = Stone,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (action) {
                            "request" -> onRequestUnbinding(reason)
                            "confirm" -> pending?.id?.let(onConfirmUnbinding)
                            "cancel" -> pending?.id?.let(onCancelUnbinding)
                            else -> onLogout()
                        }
                        confirm = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Rose),
                ) { Text("确认") }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirm = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = Stone),
                ) { Text("取消") }
            },
        )
    }
}

@Composable
private fun Avatar(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(72.dp)
            .background(Color.White, CircleShape)
            .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Rose.copy(alpha = 0.9f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = Color.White, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(22.dp),
        color = SoftSurface,
        border = BorderStroke(1.dp, SoftOutline.copy(alpha = 0.65f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(Blush, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Rose, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = Stone)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Stone.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun EmptyHint(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(
        Modifier.fillMaxWidth().padding(38.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = Rose.copy(alpha = .55f), modifier = Modifier.size(58.dp))
        Spacer(Modifier.height(12.dp))
        Text(text, color = Stone)
    }
}

@Composable
private fun MediaDetail(item: MediaItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val player = if (item.type == MediaType.VIDEO) {
        remember(item.url) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(PlayerMediaItem.fromUri(item.url))
                prepare()
                playWhenReady = true
            }
        }
    } else {
        null
    }
    DisposableEffect(player) {
        onDispose { player?.release() }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (player != null) {
                AndroidView(
                    factory = { viewContext ->
                        PlayerView(viewContext).apply {
                            useController = true
                            this.player = player
                        }
                    },
                    update = { it.player = player },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                AsyncImage(
                    item.url,
                    item.caption,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)) {
                Icon(Icons.Rounded.Close, "关闭", tint = Color.White)
            }
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .9f))))
                    .padding(24.dp).navigationBarsPadding(),
            ) {
                Text(item.caption, color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text(item.mediaDate, color = Color.White.copy(alpha = .7f))
            }
        }
    }
}

@Composable
private fun LetterDetail(letter: Letter, onDismiss: () -> Unit) {
    val unlocked = letter.isUnlocked
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (unlocked) Icons.Rounded.Favorite else Icons.Rounded.Lock, null, tint = Rose) },
        title = { Text(letter.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (unlocked) letter.content.orEmpty() else "这封时间胶囊将在 ${letter.unlockAt?.take(10)} 解锁。")
                HorizontalDivider()
                Text("${letter.senderNickname} · ${letter.createdAt.take(10)}", color = Stone)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("收好") } },
    )
}
