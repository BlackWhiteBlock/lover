@file:OptIn(ExperimentalMaterial3Api::class)

package com.lover.app.feature.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.lover.app.core.design.*
import com.lover.app.core.model.*

internal const val MaxMediaPick = 9

enum class Editor { MEDIA, ANNIVERSARY, LETTER }

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.data.collectAsState()
    val tab by viewModel.selectedTab.collectAsState()
    var editor by remember { mutableStateOf<Editor?>(null) }
    var mediaDetail by remember { mutableStateOf<MediaItem?>(null) }
    var letterDetail by remember { mutableStateOf<Letter?>(null) }
    // 按用户区分；不持久化，避免换号/重新登录后弹窗被永久抑制
    var postponedBindIds by remember(state.user?.id) { mutableStateOf(listOf<String>()) }
    val composing = editor != null
    val mediaDetailOpen = mediaDetail != null
    val overlayOpen = composing || mediaDetailOpen
    val incomingBind = remember(state.pendingIncomingBinds, state.couple?.pendingIncomingBinds, state.linked) {
        if (state.linked) {
            null
        } else {
            state.pendingIncomingBinds.firstOrNull { it.id.isNotBlank() }
                ?: state.couple?.pendingIncomingBinds.orEmpty().firstOrNull { it.id.isNotBlank() }
        }
    }
    val showBindPrompt = incomingBind != null &&
        tab != MainTab.PROFILE &&
        incomingBind.id !in postponedBindIds

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = WarmBackground,
            bottomBar = {
                if (!overlayOpen) {
                    LoverNavigation(selected = tab, onSelect = viewModel::selectTab)
                }
            },
            floatingActionButton = {
                if (!overlayOpen) {
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
                            viewModel = viewModel,
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
                    linked = state.linked,
                    onDismiss = { editor = null },
                    onSaveMedia = { uris, caption, date ->
                        viewModel.addMedia(uris, caption, date)
                        editor = null
                    },
                    onSaveAnniversary = { title, date, type ->
                        viewModel.addAnniversary(title, date, type)
                        editor = null
                    },
                    onSaveLetter = { title, content, type, date, unlockOnBind ->
                        viewModel.addLetter(title, content, type, date, unlockOnBind)
                        editor = null
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = mediaDetailOpen,
            enter = fadeIn(tween(280)) + slideInVertically(tween(320)) { it / 10 },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 12 },
        ) {
            val current = mediaDetail
            if (current != null) {
                // 列表刷新后尽量跟新一项（签名 URL 可能更新）
                val live = state.media.firstOrNull { it.id == current.id } ?: current
                MediaDetailScreen(
                    item = live,
                    members = state.couple?.members.orEmpty(),
                    onClose = { mediaDetail = null },
                    onSave = { caption, date ->
                        viewModel.updateMedia(live.id, caption, date)
                        mediaDetail = null
                    },
                    onDelete = {
                        viewModel.deleteMedia(live.id)
                        mediaDetail = null
                    },
                )
            }
        }

        letterDetail?.let { LetterDetail(it) { letterDetail = null } }

        if (showBindPrompt && incomingBind != null) {
            IncomingBindDialog(
                inviterLabel = bindInviterLabel(incomingBind.requesterNickname, incomingBind.requesterPhone),
                onGoNow = {
                    if (incomingBind.id !in postponedBindIds) {
                        postponedBindIds = postponedBindIds + incomingBind.id
                    }
                    viewModel.selectTab(MainTab.PROFILE)
                },
                onLater = {
                    if (incomingBind.id !in postponedBindIds) {
                        postponedBindIds = postponedBindIds + incomingBind.id
                    }
                },
            )
        }
    }
}

private fun bindInviterLabel(nickname: String, phone: String): String {
    val name = nickname.ifBlank { "对方" }
    val tail = phone.filter(Char::isDigit).takeLast(4).ifBlank { "????" }
    return "$name($tail)"
}

@Composable
private fun IncomingBindDialog(
    inviterLabel: String,
    onGoNow: () -> Unit,
    onLater: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
        shape = RoundedCornerShape(28.dp),
        containerColor = WarmBackground,
        title = { Text("收到绑定邀请") },
        text = {
            Text(
                "有一个来自「$inviterLabel」的绑定邀请，请及时到「我们」里去处理",
                color = Stone,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onGoNow,
                colors = ButtonDefaults.textButtonColors(contentColor = Rose),
            ) { Text("马上去") }
        },
        dismissButton = {
            TextButton(
                onClick = onLater,
                colors = ButtonDefaults.textButtonColors(contentColor = Stone),
            ) { Text("暂时不处理") }
        },
    )
}

@Composable
private fun LoverNavigation(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
) {
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
            LoverBrandRow(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                logoSize = 44.dp,
            )
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
                    LoverLogoPhoto(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 18.dp)
                            .graphicsLayer { rotationZ = -14f }
                            .alpha(0.28f),
                        size = 96.dp,
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
        if (item.type == MediaType.VIDEO && item.assetCount <= 1) {
            Icon(
                Icons.Rounded.PlayCircle,
                "视频",
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(44.dp),
            )
        }
        if (item.assetCount > 1) {
            Text(
                "${item.assetCount}",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
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
    viewModel: MainViewModel,
    onLogout: () -> Unit,
    onRequestUnbinding: (String?) -> Unit,
    onConfirmUnbinding: (String) -> Unit,
    onCancelUnbinding: (String) -> Unit,
) {
    var confirm by remember { mutableStateOf<String?>(null) }
    var reason by rememberSaveable { mutableStateOf("") }
    var showBindSheet by rememberSaveable { mutableStateOf(false) }
    var togetherDraft by rememberSaveable { mutableStateOf(java.time.LocalDate.now().minusYears(1).toString()) }
    val promptTogether by viewModel.promptTogetherDate.collectAsState()
    val partner = state.couple?.members?.firstOrNull { it.id != state.user?.id }
    val hasPartner = state.linked && partner != null
    val pending = state.couple?.pendingUnbinding
    val incoming = state.pendingIncomingBinds.ifEmpty {
        state.couple?.pendingIncomingBinds.orEmpty()
    }
    val outgoing = state.pendingOutgoingBind
        ?: state.couple?.pendingOutgoingBind

    LaunchedEffect(hasPartner) {
        if (hasPartner) showBindSheet = false
    }

    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
        item {
            PageHeader("我们的小宇宙", "Our Private World")
            if (!hasPartner) {
                val invite = incoming.firstOrNull { it.id.isNotBlank() }
                if (invite != null) {
                    IncomingBindCard(
                        meNickname = state.user?.nickname,
                        meAvatarUrl = state.user?.avatarUrl,
                        inviterNickname = invite.requesterNickname,
                        inviterAvatarUrl = invite.requesterAvatarUrl,
                        inviterLabel = bindInviterLabel(invite.requesterNickname, invite.requesterPhone),
                        onAccept = { viewModel.acceptBind(invite.id) },
                        onReject = { viewModel.rejectBind(invite.id) },
                    )
                } else {
                    EmptyCoupleCard(
                        meNickname = state.user?.nickname,
                        meAvatarUrl = state.user?.avatarUrl,
                        outgoing = outgoing?.takeIf { it.id.isNotBlank() },
                        onBind = { showBindSheet = true },
                        onCancelOutgoing = outgoing?.id?.takeIf { it.isNotBlank() }?.let { id ->
                            { viewModel.cancelBind(id) }
                        },
                    )
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Blush.copy(alpha = 0.85f)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    elevation = CardDefaults.cardElevation(0.dp),
                ) {
                    Column(
                        Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CoupleBondVisual(
                            mode = CoupleBondMode.Bound,
                            leftNickname = state.user?.nickname ?: "我",
                            leftAvatarUrl = state.user?.avatarUrl,
                            rightNickname = partner!!.nickname,
                            rightAvatarUrl = partner.avatarUrl,
                        )
                        Text(state.couple?.name ?: "我们", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Established ${state.couple?.togetherDate?.replace('-', '.') ?: "待设置"}",
                            color = Stone,
                        )
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
            if (hasPartner) {
                if (pending == null) {
                    OutlinedButton(
                        onClick = { confirm = "request" },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, SoftOutline),
                    ) { Text("申请解除情侣绑定") }
                } else if (pending.requestedBy == state.user?.id) {
                    Text("解绑申请等待伴侣确认", color = Stone)
                    OutlinedButton(
                        onClick = { confirm = "cancel" },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                    ) { Text("取消解绑申请") }
                } else {
                    Text("伴侣发起了解绑申请", color = MaterialTheme.colorScheme.error)
                    Button(
                        onClick = { confirm = "confirm" },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Rose),
                    ) { Text("确认解除绑定") }
                    TextButton(onClick = { confirm = "cancel" }, modifier = Modifier.fillMaxWidth()) {
                        Text("拒绝并取消申请")
                    }
                }
            }
            TextButton(onClick = { confirm = "logout" }, modifier = Modifier.fillMaxWidth()) { Text("退出登录") }
        }
    }

    if (showBindSheet) {
        PhoneBindSheet(
            onDismiss = { showBindSheet = false },
            onConfirm = { phone ->
                viewModel.requestBind(phone)
                showBindSheet = false
            },
        )
    }

    if (promptTogether) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissTogetherDatePrompt() },
            shape = RoundedCornerShape(28.dp),
            containerColor = WarmBackground,
            title = { Text("设置在一起的日子？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("可以稍后在「我们」里再设置", color = Stone)
                    LoverDateField(
                        value = togetherDraft,
                        onValueChange = { togetherDraft = it },
                        label = "在一起的那天",
                        maxDate = java.time.LocalDate.now(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.saveTogetherDate(togetherDraft) },
                    colors = ButtonDefaults.textButtonColors(contentColor = Rose),
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissTogetherDatePrompt() },
                    colors = ButtonDefaults.textButtonColors(contentColor = Stone),
                ) { Text("跳过") }
            },
        )
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
private fun EmptyCoupleCard(
    meNickname: String?,
    meAvatarUrl: String?,
    outgoing: OutgoingBindRequest?,
    onBind: () -> Unit,
    onCancelOutgoing: (() -> Unit)?,
) {
    val outgoingLabel = outgoing?.let {
        bindInviterLabel(it.targetNickname, it.targetPhone)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = Blush.copy(alpha = 0.85f)),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (outgoing != null) {
                CoupleBondVisual(
                    mode = CoupleBondMode.Linking,
                    leftNickname = meNickname ?: "我",
                    leftAvatarUrl = meAvatarUrl,
                    rightNickname = outgoing.targetNickname.ifBlank { outgoingLabel ?: "对方" },
                    rightAvatarUrl = outgoing.targetAvatarUrl,
                )
            } else {
                CoupleBondVisual(
                    mode = CoupleBondMode.Empty,
                    leftNickname = meNickname ?: "我",
                    leftAvatarUrl = meAvatarUrl,
                    rightNickname = "?",
                    rightAvatarUrl = null,
                )
            }
            Text("我们的小宇宙", style = MaterialTheme.typography.headlineMedium)
            if (outgoing != null && outgoingLabel != null) {
                Text(
                    "已向 $outgoingLabel 发送绑定邀请",
                    color = Stone,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "状态：等待对方确认",
                    color = Rose,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (onCancelOutgoing != null) {
                    TextButton(onClick = onCancelOutgoing) { Text("取消请求") }
                }
            } else {
                Text(
                    "现在还没有我们喔，快去绑定另一半吧",
                    color = Stone,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onBind,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Rose),
                ) { Text("绑定另一半") }
            }
        }
    }
}

@Composable
private fun IncomingBindCard(
    meNickname: String?,
    meAvatarUrl: String?,
    inviterNickname: String,
    inviterAvatarUrl: String?,
    inviterLabel: String,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Blush.copy(alpha = 0.85f)),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CoupleBondVisual(
                mode = CoupleBondMode.Linking,
                leftNickname = inviterNickname.ifBlank { "对方" },
                leftAvatarUrl = inviterAvatarUrl,
                rightNickname = meNickname ?: "我",
                rightAvatarUrl = meAvatarUrl,
            )
            Text("我们的小宇宙", style = MaterialTheme.typography.headlineMedium)
            Text(
                "${inviterLabel}邀请您绑定",
                color = Stone,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, SoftOutline),
                ) { Text("拒绝") }
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Rose),
                ) { Text("同意") }
            }
        }
    }
}

private enum class CoupleBondMode { Empty, Linking, Bound }

@Composable
private fun CoupleBondVisual(
    mode: CoupleBondMode,
    leftNickname: String,
    leftAvatarUrl: String?,
    rightNickname: String,
    rightAvatarUrl: String?,
) {
    when (mode) {
        CoupleBondMode.Bound -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PersonAvatar(
                        nickname = leftNickname,
                        avatarUrl = leftAvatarUrl,
                        modifier = Modifier.size(72.dp),
                    )
                    PersonAvatar(
                        nickname = rightNickname,
                        avatarUrl = rightAvatarUrl,
                        modifier = Modifier
                            .offset(x = (-14).dp)
                            .size(72.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "${displayName(leftNickname)} · ${displayName(rightNickname)}",
                    color = Stone,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        CoupleBondMode.Linking -> {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                PersonAvatarWithName(nickname = leftNickname, avatarUrl = leftAvatarUrl)
                Box(modifier = Modifier.padding(top = 28.dp, start = 6.dp, end = 6.dp)) {
                    LinkingPulse()
                }
                PersonAvatarWithName(nickname = rightNickname, avatarUrl = rightAvatarUrl)
            }
        }
        CoupleBondMode.Empty -> {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                PersonAvatarWithName(nickname = leftNickname, avatarUrl = leftAvatarUrl)
                Spacer(Modifier.width(18.dp))
                PersonAvatarWithName(
                    nickname = rightNickname,
                    avatarUrl = null,
                    placeholderMark = true,
                )
            }
        }
    }
}

@Composable
private fun PersonAvatarWithName(
    nickname: String,
    avatarUrl: String?,
    placeholderMark: Boolean = false,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(max = 96.dp),
    ) {
        PersonAvatar(
            nickname = nickname,
            avatarUrl = avatarUrl,
            placeholderMark = placeholderMark,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (placeholderMark) "待绑定" else displayName(nickname),
            color = Stone,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LinkingPulse() {
    val transition = rememberInfiniteTransition(label = "link")
    val travel by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "travel",
    )
    val glow by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )
    Box(
        modifier = Modifier.width(56.dp).height(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Rose.copy(alpha = 0.22f * glow), RoundedCornerShape(2.dp)),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { index ->
                val phase = ((travel + index / 3f) % 1f)
                val alpha = (1f - kotlin.math.abs(phase - 0.5f) * 2f).coerceIn(0.2f, 1f)
                Box(
                    Modifier
                        .size(6.dp)
                        .graphicsLayer { this.alpha = alpha * glow }
                        .background(Rose, CircleShape),
                )
            }
        }
        Icon(
            Icons.Rounded.Favorite,
            contentDescription = null,
            tint = Rose.copy(alpha = 0.55f + 0.35f * glow),
            modifier = Modifier
                .size(14.dp)
                .graphicsLayer {
                    translationX = (travel - 0.5f) * 36f
                    scaleX = 0.85f + 0.2f * glow
                    scaleY = 0.85f + 0.2f * glow
                },
        )
    }
}

@Composable
private fun PersonAvatar(
    nickname: String,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    placeholderMark: Boolean = false,
) {
    val initial = displayName(nickname).take(1).ifBlank { "我" }
    Box(
        modifier
            .background(Color.White, CircleShape)
            .border(2.dp, Color.White, CircleShape)
            .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(if (placeholderMark) Blush else Rose.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                !avatarUrl.isNullOrBlank() -> {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = nickname,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                placeholderMark -> {
                    Text("?", color = Rose, style = MaterialTheme.typography.headlineMedium)
                }
                else -> {
                    Text(
                        initial,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }
        }
    }
}

private fun displayName(nickname: String): String =
    nickname.trim().ifBlank { "我" }.take(12)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneBindSheet(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var phone by rememberSaveable { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = WarmBackground,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("绑定另一半", style = MaterialTheme.typography.headlineMedium)
            Text("输入对方已注册的手机号，等待对方同意后完成绑定", color = Stone)
            SoftTextField(
                value = phone,
                onValueChange = { phone = it.filter(Char::isDigit).take(11) },
                label = "对方手机号",
                placeholder = "11 位手机号",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onConfirm(phone) },
                enabled = phone.length == 11,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Rose),
            ) { Text("发送绑定请求") }
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
