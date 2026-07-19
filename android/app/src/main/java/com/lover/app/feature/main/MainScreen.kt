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
import androidx.activity.compose.rememberLauncherForActivityResult
import android.net.Uri
import kotlinx.coroutines.delay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.lover.app.BuildConfig
import com.lover.app.R
import com.lover.app.core.design.*
import com.lover.app.core.media.LocalMediaThumb
import com.lover.app.core.media.PickGalleryImage
import com.lover.app.core.media.listMediaImageRequest
import com.lover.app.core.media.signedMediaImageRequest
import com.lover.app.core.model.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal const val MaxMediaPick = 20

enum class Editor { MEDIA, ANNIVERSARY, LETTER }

/** Inclusive calendar days since registration (registration day = 1). */
internal fun waitingDaysSinceRegistration(createdAt: String?): Int {
    val raw = createdAt?.trim().orEmpty()
    if (raw.isBlank()) return 1
    val datePart = raw.take(10)
    val start = runCatching { LocalDate.parse(datePart) }.getOrNull() ?: return 1
    val days = ChronoUnit.DAYS.between(start, LocalDate.now()).toInt()
    return (days + 1).coerceAtLeast(1)
}

@Composable
private fun NavTabIcon(
    tab: MainTab,
    label: String,
    soloMode: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    when {
        tab == MainTab.ANNIVERSARY && !soloMode -> {
            Icon(
                painter = painterResource(R.drawable.ic_nav_anniversary),
                contentDescription = label,
                tint = tint,
                modifier = modifier,
            )
        }
        tab == MainTab.PROFILE && !soloMode -> {
            Icon(
                painter = painterResource(R.drawable.ic_nav_us),
                contentDescription = label,
                tint = tint,
                modifier = modifier,
            )
        }
        else -> {
            val vector: ImageVector = when (tab) {
                MainTab.HOME -> Icons.Rounded.Favorite
                MainTab.TIMELINE -> Icons.Rounded.PhotoLibrary
                MainTab.ANNIVERSARY -> Icons.Rounded.Event
                MainTab.LETTERS -> Icons.Rounded.Mail
                MainTab.PROFILE -> Icons.Rounded.Person
            }
            Icon(vector, label, modifier = modifier, tint = tint)
        }
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    deferTogetherDatePrompt: Boolean = false,
) {
    val state by viewModel.data.collectAsState()
    val tab by viewModel.selectedTab.collectAsState()
    val pendingUploads by viewModel.pendingMediaUploads.collectAsState()
    val promptTogether by viewModel.promptTogetherDate.collectAsState()
    val mediaUnreadCount by viewModel.mediaUnreadCount.collectAsState()
    val letterUnreadCount by viewModel.letterUnreadCount.collectAsState()
    val mediaHasMore by viewModel.mediaHasMore.collectAsState()
    val mediaYears by viewModel.mediaYears.collectAsState()
    val mediaYearFilter by viewModel.mediaYearFilter.collectAsState()
    val scope = rememberCoroutineScope()
    var editor by remember { mutableStateOf<Editor?>(null) }
    var mediaDetail by remember { mutableStateOf<MediaItem?>(null) }
    var mediaEdit by remember { mutableStateOf<MediaItem?>(null) }
    var letterDetail by remember { mutableStateOf<Letter?>(null) }
    var letterOpenAnimated by remember { mutableStateOf(false) }
    var anniversaryEdit by remember { mutableStateOf<Anniversary?>(null) }
    var showUnreadMedia by remember { mutableStateOf(false) }
    var togetherDraft by rememberSaveable {
        mutableStateOf(java.time.LocalDate.now().minusYears(1).toString())
    }
    // 按用户区分；不持久化，避免换号/重新登录后弹窗被永久抑制
    var postponedBindIds by remember(state.user?.id) { mutableStateOf(listOf<String>()) }
    val composing = editor != null
    val mediaDetailOpen = mediaDetail != null
    val mediaEditOpen = mediaEdit != null
    val anniversaryEditOpen = anniversaryEdit != null
    val letterDetailOpen = letterDetail != null
    val overlayOpen =
        composing || mediaDetailOpen || mediaEditOpen || anniversaryEditOpen || showUnreadMedia || letterDetailOpen
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

    val mood = LocalMood.current
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = mood.background,
            bottomBar = {
                if (!overlayOpen) {
                    LoverNavigation(
                        selected = tab,
                        onSelect = viewModel::selectTab,
                        soloMode = !state.linked,
                        timelineUnread = mediaUnreadCount > 0,
                        lettersUnread = letterUnreadCount > 0,
                    )
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
                            containerColor = mood.soft,
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
                            onMedia = { item ->
                                viewModel.openMediaDetail(item) { mediaDetail = it }
                            },
                            onViewAllTimeline = { viewModel.selectTab(MainTab.TIMELINE) },
                            onGoSetTogetherDate = { viewModel.selectTab(MainTab.PROFILE) },
                        )
                        MainTab.TIMELINE -> TimelinePage(
                            media = state.media,
                            pendingUploads = pendingUploads,
                            unreadCount = mediaUnreadCount,
                            years = mediaYears,
                            selectedYear = mediaYearFilter,
                            hasMore = mediaHasMore,
                            onYearSelected = viewModel::setMediaYearFilter,
                            onOpenUnread = { showUnreadMedia = true },
                            onMedia = { item -> viewModel.openMediaDetail(item) { mediaDetail = it } },
                            onLoadMore = { viewModel.loadMoreMedia() },
                        )
                        MainTab.ANNIVERSARY -> AnniversaryPage(
                            anniversaries = state.anniversaries,
                            viewModel = viewModel,
                            onAdd = { editor = Editor.ANNIVERSARY },
                            onEdit = { anniversaryEdit = it },
                        )
                        MainTab.LETTERS -> LettersPage(
                            letters = state.letters,
                            currentUserId = state.user?.id,
                            onLetter = { letter ->
                                val sealed = letter.isSealedFor(state.user?.id)
                                if (sealed) {
                                    scope.launch {
                                        val opened = viewModel.openLetter(letter.id) ?: return@launch
                                        letterOpenAnimated = true
                                        letterDetail = opened
                                    }
                                } else {
                                    letterOpenAnimated = false
                                    letterDetail = letter
                                }
                            },
                            onAdd = { editor = Editor.LETTER },
                        )
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
            visible = mediaDetailOpen && !mediaEditOpen,
            enter = slideInHorizontally(
                animationSpec = tween(450, easing = FastOutSlowInEasing),
                initialOffsetX = { it },
            ) + fadeIn(tween(280)),
            exit = slideOutHorizontally(
                animationSpec = tween(350, easing = FastOutSlowInEasing),
                targetOffsetX = { it },
            ) + fadeOut(tween(200)),
        ) {
            val current = mediaDetail
            if (current != null) {
                val cached = state.media.firstOrNull { it.id == current.id }
                val live = if (cached != null) {
                    current.copy(
                        caption = cached.caption,
                        mediaDate = cached.mediaDate,
                        assets = if (current.assets.any { it.url.isNotBlank() }) {
                            // 保留已签发原图，缩略图用列表最新
                            current.assets.map { part ->
                                val fresh = cached.assets.firstOrNull { it.assetId == part.assetId }
                                if (fresh != null && part.url.isNotBlank()) {
                                    fresh.copy(url = part.url, thumbnailUrl = fresh.thumbnailUrl ?: part.thumbnailUrl)
                                } else {
                                    fresh ?: part
                                }
                            }
                        } else {
                            cached.assets
                        },
                    )
                } else {
                    current
                }
                MediaDetailScreen(
                    item = live,
                    members = state.couple?.members.orEmpty(),
                    onClose = { mediaDetail = null },
                    onEdit = { mediaEdit = live },
                )
            }
        }

        AnimatedVisibility(
            visible = mediaEditOpen,
            enter = slideInHorizontally(
                animationSpec = tween(400, easing = FastOutSlowInEasing),
                initialOffsetX = { it },
            ) + fadeIn(tween(220)),
            exit = slideOutHorizontally(
                animationSpec = tween(320, easing = FastOutSlowInEasing),
                targetOffsetX = { it },
            ) + fadeOut(tween(180)),
        ) {
            val editing = mediaEdit
            if (editing != null) {
                val cached = state.media.firstOrNull { it.id == editing.id } ?: editing
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(LocalMood.current.background),
                ) {
                    MediaEditScreen(
                        item = cached,
                        currentUserId = state.user?.id,
                        onClose = {
                            // 返回详情页
                            mediaDetail = cached
                            mediaEdit = null
                        },
                        onSave = { caption, date, assetOrder ->
                            viewModel.updateMedia(
                                id = cached.id,
                                caption = caption,
                                date = date,
                                assetOrder = assetOrder,
                            )
                            mediaDetail = cached
                            mediaEdit = null
                        },
                        onDelete = {
                            viewModel.deleteMedia(cached.id)
                            mediaEdit = null
                            mediaDetail = null
                        },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = anniversaryEditOpen,
            enter = slideInHorizontally(
                animationSpec = tween(400, easing = FastOutSlowInEasing),
                initialOffsetX = { it },
            ) + fadeIn(tween(220)),
            exit = slideOutHorizontally(
                animationSpec = tween(320, easing = FastOutSlowInEasing),
                targetOffsetX = { it },
            ) + fadeOut(tween(180)),
        ) {
            val editing = anniversaryEdit
            if (editing != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(LocalMood.current.background),
                ) {
                    AnniversaryEditScreen(
                        item = editing,
                        onClose = { anniversaryEdit = null },
                        onSave = { title, date, type ->
                            viewModel.updateAnniversary(editing.id, title, date, type)
                            anniversaryEdit = null
                        },
                        onDelete = {
                            viewModel.deleteAnniversary(editing.id)
                            anniversaryEdit = null
                        },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = letterDetailOpen,
            enter = slideInHorizontally(
                animationSpec = tween(420, easing = FastOutSlowInEasing),
                initialOffsetX = { it },
            ) + fadeIn(tween(260)),
            exit = slideOutHorizontally(
                animationSpec = tween(320, easing = FastOutSlowInEasing),
                targetOffsetX = { it },
            ) + fadeOut(tween(180)),
        ) {
            val current = letterDetail
            if (current != null) {
                val live = state.letters.firstOrNull { it.id == current.id } ?: current
                LetterDetailScreen(
                    letter = live,
                    currentUserId = state.user?.id,
                    playOpenAnimation = letterOpenAnimated,
                    onClose = {
                        letterDetail = null
                        letterOpenAnimated = false
                    },
                    onDelete = { viewModel.deleteLetter(live.id) },
                )
            }
        }

        if (showUnreadMedia) {
            UnreadMediaSheet(
                loadPage = viewModel::loadUnreadMediaPage,
                onDismiss = { showUnreadMedia = false },
                onOpenItem = { item ->
                    showUnreadMedia = false
                    viewModel.openMediaDetail(item) { mediaDetail = it }
                },
                onMarkAllRead = { viewModel.markAllUnreadMediaRead() },
            )
        }

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

        if (promptTogether && !deferTogetherDatePrompt) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissTogetherDatePrompt() },
                shape = RoundedCornerShape(28.dp),
                containerColor = mood.background,
                title = { Text("设置在一起的日子？") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("可以稍后在「我们」里再设置", color = mood.stone)
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
                        colors = ButtonDefaults.textButtonColors(contentColor = mood.soft),
                    ) { Text("保存") }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.dismissTogetherDatePrompt() },
                        colors = ButtonDefaults.textButtonColors(contentColor = mood.stone),
                    ) { Text("跳过") }
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
        containerColor = LocalMood.current.background,
        title = { Text("收到绑定邀请") },
        text = {
            Text(
                "有一个来自「$inviterLabel」的绑定邀请，请及时到「我」里去处理",
                color = LocalMood.current.stone,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onGoNow,
                colors = ButtonDefaults.textButtonColors(contentColor = LocalMood.current.soft),
            ) { Text("马上去") }
        },
        dismissButton = {
            TextButton(
                onClick = onLater,
                colors = ButtonDefaults.textButtonColors(contentColor = LocalMood.current.stone),
            ) { Text("暂时不处理") }
        },
    )
}

@Composable
private fun LoverNavigation(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    soloMode: Boolean,
    timelineUnread: Boolean = false,
    lettersUnread: Boolean = false,
) {
    val mood = LocalMood.current
    val tabs = listOf(
        MainTab.HOME to "空间",
        MainTab.TIMELINE to "时光",
        MainTab.ANNIVERSARY to "纪念",
        MainTab.LETTERS to "信封",
        MainTab.PROFILE to if (soloMode) "我" else "我们",
    )
    Surface(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        shape = RoundedCornerShape(36.dp),
        shadowElevation = 8.dp,
        tonalElevation = 0.dp,
        color = (if (soloMode) mood.softSurface else Color.White).copy(alpha = .96f),
        border = BorderStroke(1.dp, mood.softOutline.copy(alpha = 0.55f)),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 4.dp)) {
            tabs.forEach { (tab, label) ->
                val scale by animateFloatAsState(
                    targetValue = if (selected == tab) 1.06f else 1f,
                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                    label = "icon",
                )
                val iconTint = if (selected == tab) mood.soft else mood.stone
                NavigationBarItem(
                    selected = selected == tab,
                    onClick = { onSelect(tab) },
                    icon = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box {
                                NavTabIcon(
                                    tab = tab,
                                    label = label,
                                    soloMode = soloMode,
                                    tint = iconTint,
                                    modifier = Modifier.scale(scale).size(24.dp),
                                )
                                if ((tab == MainTab.TIMELINE && timelineUnread) ||
                                    (tab == MainTab.LETTERS && lettersUnread)
                                ) {
                                    Box(
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 3.dp, y = (-2).dp)
                                            .size(8.dp)
                                            .background(Color(0xFFE53935), CircleShape),
                                    )
                                }
                            }
                            if (selected == tab) {
                                Box(
                                    Modifier
                                        .padding(top = 3.dp)
                                        .size(width = 12.dp, height = 3.dp)
                                        .background(mood.soft, RoundedCornerShape(2.dp)),
                                )
                            }
                        }
                    },
                    label = { Text(label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = mood.soft,
                        selectedTextColor = mood.soft,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = mood.stone,
                        unselectedTextColor = mood.stone,
                    ),
                )
            }
        }
    }
}

@Composable
private fun PageHeader(
    title: String,
    subtitle: String,
    action: (@Composable () -> Unit)? = null,
    bottomPadding: Dp = 18.dp,
) {
    val mood = LocalMood.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = bottomPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = mood.accent.copy(alpha = 0.92f))
            Text(subtitle.uppercase(), style = MaterialTheme.typography.labelSmall, color = mood.stone)
        }
        action?.invoke()
    }
}

@Composable
private fun HomePage(
    state: AppState,
    onMedia: (MediaItem) -> Unit,
    onViewAllTimeline: () -> Unit,
    onGoSetTogetherDate: () -> Unit,
) {
    val mood = LocalMood.current
    val days = state.lovingDays
    val togetherUnset = !mood.solo && (
        state.needsTogetherDate ||
            state.couple?.togetherDate.isNullOrBlank() ||
            days == null
        )
    val waitingDays = remember(state.user?.createdAt) {
        waitingDaysSinceRegistration(state.user?.createdAt)
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            HomeTopBar(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
            )
        }
        item {
            Card(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .then(
                        if (togetherUnset) {
                            Modifier.clickable(onClick = onGoSetTogetherDate)
                        } else {
                            Modifier
                        },
                    ),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(34.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(mood.blush, mood.peach.copy(alpha = .7f)),
                            ),
                            RoundedCornerShape(34.dp),
                        )
                        .padding(30.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (mood.solo) {
                            Text(
                                "WAITING",
                                style = MaterialTheme.typography.labelMedium,
                                color = mood.accent,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "等待你的出现",
                                style = MaterialTheme.typography.headlineMedium,
                                color = mood.accent.copy(alpha = 0.92f),
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    "$waitingDays",
                                    style = MaterialTheme.typography.displayLarge,
                                    color = mood.accent,
                                )
                                Text(
                                    " 天",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                    color = mood.stone,
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "每一天，都是给未来的礼物",
                                color = mood.stone,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                        } else if (togetherUnset) {
                            Text(
                                "Loving Journey",
                                style = MaterialTheme.typography.labelMedium,
                                color = mood.accent,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "还未设置在一起的时间",
                                style = MaterialTheme.typography.headlineMedium,
                                color = mood.accent.copy(alpha = 0.92f),
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "请到「我们」中设置",
                                color = mood.stone,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                        } else {
                            Text(
                                "Loving Journey",
                                style = MaterialTheme.typography.labelMedium,
                                color = mood.accent,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("$days", style = MaterialTheme.typography.displayLarge)
                                Text(
                                    " 天",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                    color = mood.stone,
                                )
                            }
                            Text("每一天，都算数", color = mood.stone)
                        }
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
            Spacer(modifier = Modifier.height(8.dp))
            DailyQuoteCard(
                quote = state.dailyQuote,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
            if (state.media.isEmpty()) {
                Text(
                    "近期掠影",
                    style = MaterialTheme.typography.titleMedium,
                    color = mood.accent.copy(alpha = 0.55f),
                    modifier = Modifier.padding(horizontal = 22.dp),
                )
                EmptyHint("还没有影像，存下第一个瞬间吧", Icons.Rounded.PhotoCamera)
            } else {
                RecentGlimpseBento(
                    media = state.media,
                    onMedia = onMedia,
                    onViewAll = onViewAllTimeline,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun TimelinePage(
    media: List<MediaItem>,
    pendingUploads: List<PendingMediaUpload>,
    unreadCount: Int,
    years: List<Int>,
    selectedYear: Int?,
    hasMore: Boolean,
    onYearSelected: (Int?) -> Unit,
    onOpenUnread: () -> Unit,
    onMedia: (MediaItem) -> Unit,
    onLoadMore: () -> Unit = {},
) {
    val mood = LocalMood.current
    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(
            if (mood.solo) "时光" else "相爱时光",
            if (mood.solo) "Quiet Moments" else "Visual Memories",
            action = {
                if (!mood.solo) {
                    UnreadMediaBadgeButton(count = unreadCount, onClick = onOpenUnread)
                }
            },
            // 有年份筛选时收紧底部间距，避免标题与 chips 过远
            bottomPadding = if (years.isNotEmpty()) 6.dp else 18.dp,
        )
        if (years.isNotEmpty()) {
            TimelineYearFilterRow(
                years = years,
                selectedYear = selectedYear,
                onYearSelected = onYearSelected,
            )
        }
        if (media.isEmpty() && pendingUploads.isEmpty()) {
            EmptyHint(
                if (selectedYear != null) "这一年还没有时光记录"
                else if (mood.solo) "选择照片或视频，记录这一刻"
                else "选择照片或视频，记录共同的故事",
                Icons.Rounded.PhotoLibrary,
            )
        } else {
            TimelineGalleryContent(
                media = media,
                pendingUploads = pendingUploads,
                onMedia = onMedia,
                onLoadMore = onLoadMore,
                hasMore = hasMore,
                uploadingCard = { pending -> UploadingMediaCard(pending) },
            )
        }
    }
}

@Composable
private fun UnreadMediaBadgeButton(count: Int, onClick: () -> Unit) {
    UnreadMemoryIcon(count = count, onClick = onClick)
}

@Composable
private fun TimelineYearFilterRow(
    years: List<Int>,
    selectedYear: Int?,
    onYearSelected: (Int?) -> Unit,
) {
    val mood = LocalMood.current
    LazyRow(
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 0.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "year-all") {
            FilterChip(
                selected = selectedYear == null,
                onClick = { onYearSelected(null) },
                label = { Text("全部") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = mood.soft.copy(alpha = 0.22f),
                    selectedLabelColor = mood.soft,
                ),
            )
        }
        items(years, key = { it }) { year ->
            FilterChip(
                selected = selectedYear == year,
                onClick = { onYearSelected(year) },
                label = { Text("${year}年") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = mood.soft.copy(alpha = 0.22f),
                    selectedLabelColor = mood.soft,
                ),
            )
        }
    }
}

@Composable
private fun UnreadMediaSheet(
    loadPage: suspend (String?) -> MediaUnreadPage?,
    onDismiss: () -> Unit,
    onOpenItem: (MediaItem) -> Unit,
    onMarkAllRead: () -> Unit,
) {
    val mood = LocalMood.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var nextCursor by remember { mutableStateOf<String?>(null) }
    var totalCount by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }

    suspend fun refreshFirstPage() {
        loading = true
        val page = loadPage(null)
        items = page?.items.orEmpty()
        nextCursor = page?.nextCursor
        totalCount = page?.count ?: 0
        loading = false
        // 打开即视为已读：角标归零、图标回静默；列表仍展示本次拉取的快照
        if (items.isNotEmpty()) {
            onMarkAllRead()
        }
    }

    LaunchedEffect(Unit) { refreshFirstPage() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mood.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("未读时光", style = MaterialTheme.typography.titleLarge, color = mood.accent)
                    Text(
                        if (totalCount > 0) "共 $totalCount 条对方更新" else "暂无未读",
                        style = MaterialTheme.typography.bodySmall,
                        color = mood.stone,
                    )
                }
                if (items.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            onMarkAllRead()
                            items = emptyList()
                            nextCursor = null
                            totalCount = 0
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = mood.soft),
                    ) { Text("全部已读") }
                }
            }

            when {
                loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = mood.soft, strokeWidth = 2.dp)
                    }
                }
                items.isEmpty() -> {
                    EmptyHint("对方的新时光会出现在这里", Icons.Rounded.NotificationsNone)
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 28.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(items, key = { it.id }) { item ->
                            UnreadMediaRow(item = item, onClick = { onOpenItem(item) })
                        }
                        item(key = "unread-footer") {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                when {
                                    nextCursor != null -> {
                                        if (!loadingMore) {
                                            LaunchedEffect(nextCursor, items.size) {
                                                loadingMore = true
                                                val page = loadPage(nextCursor)
                                                if (page != null) {
                                                    val existing = items.map { it.id }.toSet()
                                                    items = items + page.items.filter { it.id !in existing }
                                                    nextCursor = page.nextCursor
                                                    totalCount = page.count
                                                } else {
                                                    nextCursor = null
                                                }
                                                loadingMore = false
                                            }
                                        }
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(22.dp),
                                            strokeWidth = 2.dp,
                                            color = mood.soft,
                                        )
                                    }
                                    else -> {
                                        Text(
                                            "没有更多未读",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = mood.stone,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnreadMediaRow(item: MediaItem, onClick: () -> Unit) {
    val mood = LocalMood.current
    val context = LocalContext.current
    val cover = item.cover
    val thumb = item.thumbnailUrl
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(mood.softSurface)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(mood.soft.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!thumb.isNullOrBlank()) {
                AsyncImage(
                    model = listMediaImageRequest(
                        context,
                        thumb,
                        cover?.assetId ?: item.id,
                    ),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Rounded.PhotoLibrary, null, tint = mood.stone)
            }
            if (cover?.type == MediaType.VIDEO) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(22.dp)
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        .padding(2.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.caption.ifBlank { "未读时光" },
                style = MaterialTheme.typography.titleSmall,
                color = mood.accent,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.mediaDate.take(10),
                style = MaterialTheme.typography.bodySmall,
                color = mood.stone,
            )
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(Color(0xFFE53935), CircleShape),
        )
    }
}

@Composable
private fun UploadingMediaCard(pending: PendingMediaUpload) {
    val pulse = rememberInfiniteTransition(label = "uploadPulse")
    val glow by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "uploadGlow",
    )
    val spin by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "uploadSpin",
    )
    val percent = (pending.progress * 100).toInt().coerceIn(0, 99)

    Box(
        Modifier
            .aspectRatio(.8f)
            .clip(RoundedCornerShape(26.dp))
            .background(LocalMood.current.blush),
    ) {
        LocalMediaThumb(
            uri = pending.previewUri,
            modifier = Modifier.fillMaxSize(),
            showVideoBadge = false,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f + glow * 0.18f)),
        )
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { pending.progress.coerceAtLeast(0.04f) },
                    modifier = Modifier
                        .size(54.dp)
                        .graphicsLayer { rotationZ = if (pending.progress < 0.05f) spin else 0f },
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    strokeWidth = 3.dp,
                )
                Text(
                    "$percent%",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                pending.phase.ifBlank { "正在上传" },
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            LinearProgressIndicator(
                progress = { pending.progress.coerceAtLeast(0.04f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = LocalMood.current.soft,
                trackColor = Color.White.copy(alpha = 0.35f),
            )
            Text(
                if (pending.total > 1) {
                    "${pending.completed.coerceAtMost(pending.total)}/${pending.total} · 后台上传中"
                } else {
                    "后台上传中，可继续浏览"
                },
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .5f))))
                .padding(12.dp),
        ) {
            Text(
                pending.caption.ifBlank { pending.mediaDate },
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (pending.assetCount > 1) {
            Text(
                "${pending.assetCount}",
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
private fun MediaImage(item: MediaItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cover = item.cover
    val thumbUrl = item.thumbnailUrl ?: item.url
    val cacheKey = cover?.assetId?.let { "media-thumb-$it" }
    Box(modifier.clip(RoundedCornerShape(26.dp)).background(LocalMood.current.blush)) {
        if (!thumbUrl.isNullOrBlank() && cacheKey != null) {
            AsyncImage(
                model = listMediaImageRequest(context, thumbUrl, cover!!.assetId),
                contentDescription = item.caption,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
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
private fun AnniversaryPage(
    anniversaries: List<Anniversary>,
    viewModel: MainViewModel,
    onAdd: () -> Unit,
    onEdit: (Anniversary) -> Unit,
) {
    val mood = LocalMood.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 签封面图 URL
    val coverUrls = remember(anniversaries.map { it.coverAssetId }) {
        mutableStateMapOf<String, String?>()
    }
    LaunchedEffect(anniversaries.map { it.coverAssetId }) {
        anniversaries.forEach { ann ->
            val assetId = ann.coverAssetId
            if (assetId != null && !coverUrls.containsKey(assetId)) {
                coverUrls[assetId] = null // placeholder
                scope.launch {
                    runCatching { viewModel.signAssetUrl(assetId) }
                        .onSuccess { coverUrls[assetId] = it }
                        .onFailure { coverUrls.remove(assetId) }
                }
            }
        }
    }
    val dashedStroke = remember(mood.soft) {
        BorderStroke(1.5.dp, mood.soft.copy(alpha = 0.35f))
    }
    Column {
        PageHeader(
            if (mood.solo) "纪念日" else "爱的纪念日",
            if (mood.solo) "Gentle Dates" else "Eternal Dates",
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (anniversaries.isEmpty()) item { EmptyHint("把重要的日子珍藏在这里", Icons.Rounded.Event) }
            items(anniversaries, key = { it.id }) { anniversary ->
                val countdown = anniversary.countdown
                val coverUrl = anniversary.coverAssetId?.let { coverUrls[it] }
                val hasCover = !coverUrl.isNullOrBlank()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(192.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .clickable { onEdit(anniversary) },
                ) {
                    // 封面图或渐变背景
                    if (hasCover) {
                        AsyncImage(
                            model = signedMediaImageRequest(context, coverUrl),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(mood.blush, mood.peach.copy(alpha = 0.6f)),
                                    ),
                                ),
                        )
                    }
                    // 渐变叠层
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent),
                                ),
                            ),
                    )
                    // 内容
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // 顶部：类型 badge
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.2f),
                        ) {
                            Text(
                                text = if (anniversary.type == AnniversaryType.MILESTONE) "里程碑" else "年度纪念",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                letterSpacing = 1.2.sp,
                            )
                        }
                        // 底部：标题 + 倒计时
                        Column {
                            Text(
                                anniversary.title,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = FontFamily.Serif,
                                ),
                                color = Color.White,
                            )
                            Text(
                                anniversary.date,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    "还有",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (countdown?.reached == true) "✓" else countdown?.days?.toString() ?: "—",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontFamily = FontFamily.Serif,
                                    ),
                                    color = Color.White,
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    if (countdown?.reached == true) "已达成" else "天",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
            item {
                // 虚线边框添加按钮
                val dashPath = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .drawBehind {
                            drawRoundRect(
                                color = mood.soft.copy(alpha = 0.35f),
                                size = size,
                                cornerRadius = CornerRadius(22.dp.toPx()),
                                style = Stroke(
                                    width = 1.5.dp.toPx(),
                                    pathEffect = dashPath,
                                ),
                            )
                        }
                        .clickable { onAdd() },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Add, null, tint = mood.soft, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("添加新的纪念日", color = mood.soft, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun LettersPage(
    letters: List<Letter>,
    currentUserId: String?,
    onLetter: (Letter) -> Unit,
    onAdd: () -> Unit,
) {
    val mood = LocalMood.current
    val dashPath = remember { PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f) }
    Column {
        PageHeader(
            if (mood.solo) "为爱信封" else "爱的信封",
            if (mood.solo) "Letters to Love" else "Secret Letters",
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (letters.isEmpty()) item { EmptyHint("给 TA 写第一封信吧", Icons.Rounded.MarkEmailUnread) }
            items(letters, key = { it.id }) { letter ->
                val unlocked = letter.isUnlocked
                val sealed = letter.isSealedFor(currentUserId)
                val isMine = currentUserId != null && letter.senderId == currentUserId
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onLetter(letter) },
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            sealed -> mood.blush.copy(alpha = 0.35f)
                            unlocked -> Color.White.copy(alpha = 0.96f)
                            else -> Color(0xFFFAF8F5)
                        },
                    ),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(if (unlocked && !sealed) 1.dp else 0.dp),
                    border = BorderStroke(1.dp, mood.softOutline.copy(alpha = 0.4f)),
                ) {
                    Box(Modifier.padding(24.dp)) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(44.dp)
                                .graphicsLayer { rotationZ = 12f }
                                .drawBehind {
                                    drawRoundRect(
                                        color = mood.soft.copy(alpha = 0.25f),
                                        size = size,
                                        cornerRadius = CornerRadius(8.dp.toPx()),
                                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = dashPath),
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (sealed) Icons.Rounded.Mail else Icons.Rounded.Favorite,
                                null,
                                tint = mood.soft.copy(alpha = if (sealed) 0.55f else 0.35f),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (letter.type == LetterType.INSTANT) mood.blush else Color(0xFFE8E4DF),
                                ) {
                                    Text(
                                        text = if (letter.type == LetterType.INSTANT) "Instant" else "Time Capsule",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                        color = if (letter.type == LetterType.INSTANT) mood.accent else mood.stone,
                                        letterSpacing = 0.8.sp,
                                    )
                                }
                                if (isMine && letter.deliveryStatus != null) {
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (letter.deliveryStatus == LetterDeliveryStatus.READ) {
                                            mood.soft.copy(alpha = 0.18f)
                                        } else {
                                            mood.softOutline.copy(alpha = 0.35f)
                                        },
                                    ) {
                                        Text(
                                            text = when (letter.deliveryStatus) {
                                                LetterDeliveryStatus.READ -> "已阅"
                                                LetterDeliveryStatus.SENT -> "已寄"
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                            color = if (letter.deliveryStatus == LetterDeliveryStatus.READ) {
                                                mood.soft
                                            } else {
                                                mood.stone
                                            },
                                        )
                                    }
                                }
                                if (sealed) {
                                    Spacer(Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFE53935).copy(alpha = 0.12f),
                                    ) {
                                        Text(
                                            "未拆",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                            color = Color(0xFFE53935),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    letter.createdAt.take(10),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = mood.stone.copy(alpha = 0.6f),
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                letter.title,
                                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif),
                                color = if (unlocked) mood.accent.copy(alpha = 0.8f) else mood.stone,
                            )
                            Spacer(Modifier.height(8.dp))
                            when {
                                !unlocked -> {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    ) {
                                        Icon(
                                            Icons.Rounded.Lock,
                                            null,
                                            tint = mood.stone.copy(alpha = 0.4f),
                                            modifier = Modifier.size(24.dp),
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            letterCapsuleLockHint(letter),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            ),
                                            color = mood.stone,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                                sealed -> {
                                    Text(
                                        "轻触拆开这封信",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        ),
                                        color = mood.soft,
                                    )
                                }
                                else -> {
                                    Text(
                                        letter.summary ?: letter.content.orEmpty(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF78716C),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 20.sp,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = mood.softOutline.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Box(modifier = Modifier.size(18.dp).background(mood.blush, CircleShape).border(1.dp, Color.White, CircleShape))
                                Box(
                                    Modifier.size(18.dp).background(mood.softSurface, CircleShape)
                                        .graphicsLayer { translationX = -6.dp.toPx() }
                                        .border(1.dp, Color.White, CircleShape),
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    "FROM ${letter.senderNickname}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = mood.stone.copy(alpha = 0.5f),
                                    letterSpacing = 1.5.sp,
                                )
                            }
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
    var showEditCard by rememberSaveable { mutableStateOf(false) }
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
            val mood = LocalMood.current
            PageHeader(
                if (mood.solo) "我的小宇宙" else "我们的小宇宙",
                if (mood.solo) "My Soft World" else "Our Private World",
            )
            if (!hasPartner) {
                val spaceName = state.couple?.name?.takeIf { it.isNotBlank() }
                    ?: if (mood.solo) "我的小宇宙" else "我们的小宇宙"
                val invite = incoming.firstOrNull { it.id.isNotBlank() }
                if (invite != null) {
                    IncomingBindCard(
                        spaceName = spaceName,
                        meNickname = state.user?.nickname,
                        meAvatarUrl = state.user?.avatarUrl,
                        inviterNickname = invite.requesterNickname,
                        inviterAvatarUrl = invite.requesterAvatarUrl,
                        inviterLabel = bindInviterLabel(invite.requesterNickname, invite.requesterPhone),
                        onEdit = { showEditCard = true },
                        onAccept = { viewModel.acceptBind(invite.id) },
                        onReject = { viewModel.rejectBind(invite.id) },
                    )
                } else {
                    EmptyCoupleCard(
                        spaceName = spaceName,
                        meNickname = state.user?.nickname,
                        meAvatarUrl = state.user?.avatarUrl,
                        outgoing = outgoing?.takeIf { it.id.isNotBlank() },
                        onEdit = { showEditCard = true },
                        onBind = { showBindSheet = true },
                        onCancelOutgoing = outgoing?.id?.takeIf { it.isNotBlank() }?.let { id ->
                            { viewModel.cancelBind(id) }
                        },
                    )
                }
            } else {
                BoundCoupleCard(
                    spaceName = state.couple?.name ?: "我们",
                    togetherDate = state.couple?.togetherDate,
                    lovingDays = state.lovingDays,
                    meNickname = state.user?.nickname ?: "我",
                    meAvatarUrl = state.user?.avatarUrl,
                    partnerNickname = partner!!.nickname,
                    partnerAvatarUrl = partner.avatarUrl,
                    coupleCoverUrl = state.user?.coupleCoverUrl,
                    onEdit = { showEditCard = true },
                )
            }
        }
        item { Spacer(Modifier.height(20.dp)); Text("空间设置", style = MaterialTheme.typography.titleLarge) }
        item { SettingRow(Icons.Rounded.Palette, "空间装扮", "暖玫瑰 · 默认主题") }
        item { SettingRow(Icons.Rounded.Lock, "隐私控制", "仅情侣双方可见") }
        item { SettingRow(Icons.Rounded.Info, "关于 Lover", "版本 ${BuildConfig.VERSION_NAME}") }
        item {
            Spacer(Modifier.height(20.dp))
            if (hasPartner) {
                if (pending == null) {
                    OutlinedButton(
                        onClick = { confirm = "request" },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, LocalMood.current.softOutline),
                    ) { Text("申请解除情侣绑定") }
                } else if (pending.requestedBy == state.user?.id) {
                    Text("解绑申请等待伴侣确认", color = LocalMood.current.stone)
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
                        colors = ButtonDefaults.buttonColors(containerColor = LocalMood.current.soft),
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
            onLookup = { phone -> viewModel.lookupUser(phone) },
            onConfirm = { phone ->
                viewModel.requestBind(phone)
                showBindSheet = false
            },
        )
    }

    if (showEditCard) {
        if (hasPartner) {
            CoupleCardEditSheet(
                initialName = state.couple?.name ?: "我们",
                initialTogetherDate = state.couple?.togetherDate,
                currentCoverUrl = state.user?.coupleCoverUrl,
                onDismiss = { showEditCard = false },
                onSave = { name, togetherDate, coverUri, clearCover ->
                    viewModel.updateCoupleCard(name, togetherDate, coverUri, clearCover)
                    showEditCard = false
                },
            )
        } else {
            PersonalCardEditSheet(
                initialName = state.couple?.name?.takeIf { it.isNotBlank() } ?: if (LocalMood.current.solo) "我的小宇宙" else "我们的小宇宙",
                currentAvatarUrl = state.user?.avatarUrl,
                onDismiss = { showEditCard = false },
                onSave = { name, avatarUri ->
                    viewModel.updatePersonalCard(name, avatarUri)
                    showEditCard = false
                },
            )
        }
    }

    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            shape = RoundedCornerShape(28.dp),
            containerColor = LocalMood.current.background,
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
                        Text("申请提交后，需要伴侣确认才会正式解绑。", color = LocalMood.current.stone)
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
                        color = LocalMood.current.stone,
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
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalMood.current.soft),
                ) { Text("确认") }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirm = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalMood.current.stone),
                ) { Text("取消") }
            },
        )
    }
}

@Composable
private fun EmptyCoupleCard(
    spaceName: String,
    meNickname: String?,
    meAvatarUrl: String?,
    outgoing: OutgoingBindRequest?,
    onEdit: () -> Unit,
    onBind: () -> Unit,
    onCancelOutgoing: (() -> Unit)?,
) {
    val outgoingLabel = outgoing?.let {
        bindInviterLabel(it.targetNickname, it.targetPhone)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = LocalMood.current.blush.copy(alpha = 0.85f)),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onEdit,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            ) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = "编辑",
                    tint = LocalMood.current.accent.copy(alpha = 0.75f),
                    modifier = Modifier.size(20.dp),
                )
            }
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
            Text(spaceName, style = MaterialTheme.typography.headlineMedium)
            if (outgoing != null && outgoingLabel != null) {
                Text(
                    "已向 $outgoingLabel 发送绑定邀请",
                    color = LocalMood.current.stone,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "状态：等待对方确认",
                    color = LocalMood.current.soft,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (onCancelOutgoing != null) {
                    TextButton(onClick = onCancelOutgoing) { Text("取消请求") }
                }
            } else {
                Text(
                    "现在还没有我们喔，快去绑定另一半吧",
                    color = LocalMood.current.stone,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onBind,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LocalMood.current.soft),
                ) { Text("绑定另一半") }
            }
        }
        }
    }
}

@Composable
private fun IncomingBindCard(
    spaceName: String,
    meNickname: String?,
    meAvatarUrl: String?,
    inviterNickname: String,
    inviterAvatarUrl: String?,
    inviterLabel: String,
    onEdit: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = LocalMood.current.blush.copy(alpha = 0.85f)),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onEdit,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            ) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = "编辑",
                    tint = LocalMood.current.accent.copy(alpha = 0.75f),
                    modifier = Modifier.size(20.dp),
                )
            }
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
            Text(spaceName, style = MaterialTheme.typography.headlineMedium)
            Text(
                "${inviterLabel}邀请您绑定",
                color = LocalMood.current.stone,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, LocalMood.current.softOutline),
                ) { Text("拒绝") }
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LocalMood.current.soft),
                ) { Text("同意") }
            }
        }
        }
    }
}

private enum class CoupleBondMode { Empty, Linking, Bound }

@Composable
private fun BoundCoupleCard(
    spaceName: String,
    togetherDate: String?,
    lovingDays: Int?,
    meNickname: String,
    meAvatarUrl: String?,
    partnerNickname: String,
    partnerAvatarUrl: String?,
    coupleCoverUrl: String?,
    onEdit: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = LocalMood.current.blush.copy(alpha = 0.85f)),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            ) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = "编辑",
                    tint = LocalMood.current.accent.copy(alpha = 0.75f),
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CoupleBondVisual(
                    mode = CoupleBondMode.Bound,
                    leftNickname = meNickname,
                    leftAvatarUrl = meAvatarUrl,
                    rightNickname = partnerNickname,
                    rightAvatarUrl = partnerAvatarUrl,
                    coupleCoverUrl = coupleCoverUrl,
                )
                Text(spaceName, style = MaterialTheme.typography.headlineMedium)
                Text(
                    togetherDateLabel(togetherDate, lovingDays),
                    color = LocalMood.current.stone,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun togetherDateLabel(togetherDate: String?, lovingDays: Int?): String {
    if (togetherDate.isNullOrBlank()) return "我们一起的时间还未设置"
    val pretty = togetherDate.replace('-', '.')
    return if (lovingDays != null && lovingDays > 0) {
        "在一起第 $lovingDays 天 · $pretty"
    } else {
        "在一起自 $pretty"
    }
}

@Composable
private fun CoupleBondVisual(
    mode: CoupleBondMode,
    leftNickname: String,
    leftAvatarUrl: String?,
    rightNickname: String,
    rightAvatarUrl: String?,
    coupleCoverUrl: String? = null,
) {
    when (mode) {
        CoupleBondMode.Bound -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (!coupleCoverUrl.isNullOrBlank()) {
                    CoupleCoverAvatar(coverUrl = coupleCoverUrl)
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 72 + 72 - 14 overlap = 130；用固定宽居中，避免 offset 造成视觉偏左
                        Box(
                            modifier = Modifier
                                .width(130.dp)
                                .height(72.dp),
                        ) {
                            PersonAvatar(
                                nickname = leftNickname,
                                avatarUrl = leftAvatarUrl,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .size(72.dp),
                            )
                            PersonAvatar(
                                nickname = rightNickname,
                                avatarUrl = rightAvatarUrl,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .size(72.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "${displayName(leftNickname)} · ${displayName(rightNickname)}",
                    color = LocalMood.current.stone,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
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
private fun CoupleCoverAvatar(coverUrl: String) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(96.dp)
            .background(Color.White, CircleShape)
            .border(2.dp, Color.White, CircleShape)
            .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = signedMediaImageRequest(context, coverUrl),
            contentDescription = "情侣头像",
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(LocalMood.current.soft.copy(alpha = 0.9f)),
            contentScale = ContentScale.Crop,
        )
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
            color = LocalMood.current.stone,
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
                .background(LocalMood.current.soft.copy(alpha = 0.22f * glow), RoundedCornerShape(2.dp)),
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
                        .background(LocalMood.current.soft, CircleShape),
                )
            }
        }
        Icon(
            Icons.Rounded.Favorite,
            contentDescription = null,
            tint = LocalMood.current.soft.copy(alpha = 0.55f + 0.35f * glow),
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
                .background(if (placeholderMark) LocalMood.current.blush else LocalMood.current.soft.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                !avatarUrl.isNullOrBlank() -> {
                    val context = LocalContext.current
                    AsyncImage(
                        model = signedMediaImageRequest(context, avatarUrl),
                        contentDescription = nickname,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                placeholderMark -> {
                    Text("?", color = LocalMood.current.soft, style = MaterialTheme.typography.headlineMedium)
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
private fun PersonalCardEditSheet(
    initialName: String,
    currentAvatarUrl: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, avatarUri: Uri?) -> Unit,
) {
    val defaultSpaceName = if (LocalMood.current.solo) "我的小宇宙" else "我们的小宇宙"
    var name by rememberSaveable { mutableStateOf(initialName) }
    var draftAvatarUri by remember { mutableStateOf<Uri?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pickAvatar = rememberLauncherForActivityResult(PickGalleryImage()) { uri ->
        if (uri != null) draftAvatarUri = uri
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = LocalMood.current.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("编辑我的空间", style = MaterialTheme.typography.headlineMedium)
            Text("可以更换头像和空间名称，绑定后双方还会有共同的空间设置", color = LocalMood.current.stone)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .background(LocalMood.current.blush)
                        .clickable { pickAvatar.launch(Unit) },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        draftAvatarUri != null -> {
                            AsyncImage(
                                model = draftAvatarUri,
                                contentDescription = "头像预览",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        !currentAvatarUrl.isNullOrBlank() -> {
                            val context = LocalContext.current
                            AsyncImage(
                                model = signedMediaImageRequest(context, currentAvatarUrl),
                                contentDescription = "头像",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        else -> {
                            Icon(
                                Icons.Rounded.AddAPhoto,
                                contentDescription = null,
                                tint = LocalMood.current.soft,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                }
                Text(
                    "点击更换头像",
                    color = LocalMood.current.stone,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable { pickAvatar.launch(Unit) },
                )
            }

            SoftTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = "空间名称",
                placeholder = defaultSpaceName,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    onSave(name.trim().ifBlank { defaultSpaceName }, draftAvatarUri)
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LocalMood.current.soft),
            ) { Text("保存") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoupleCardEditSheet(
    initialName: String,
    initialTogetherDate: String?,
    currentCoverUrl: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, togetherDate: String?, coverUri: Uri?, clearCover: Boolean) -> Unit,
) {
    val defaultSpaceName = if (LocalMood.current.solo) "我的小宇宙" else "我们的小宇宙"
    var name by rememberSaveable { mutableStateOf(initialName) }
    var togetherDate by rememberSaveable {
        mutableStateOf(initialTogetherDate.orEmpty())
    }
    var dateTouched by rememberSaveable { mutableStateOf(false) }
    var draftCoverUri by remember { mutableStateOf<Uri?>(null) }
    var clearCover by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pickCover = rememberLauncherForActivityResult(PickGalleryImage()) { uri ->
        if (uri != null) {
            draftCoverUri = uri
            clearCover = false
        }
    }
    val previewUrl = when {
        clearCover -> null
        draftCoverUri != null -> draftCoverUri.toString()
        else -> currentCoverUrl
    }
    val hasCover = !previewUrl.isNullOrBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = LocalMood.current.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("编辑我们", style = MaterialTheme.typography.headlineMedium)
            Text("情侣头像仅影响你这边的展示；空间名称与在一起的日子会同步给双方", color = LocalMood.current.stone)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .background(LocalMood.current.blush)
                        .clickable { pickCover.launch(Unit) },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        draftCoverUri != null -> {
                            AsyncImage(
                                model = draftCoverUri,
                                contentDescription = "情侣头像预览",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        !clearCover && !currentCoverUrl.isNullOrBlank() -> {
                            val context = LocalContext.current
                            AsyncImage(
                                model = signedMediaImageRequest(context, currentCoverUrl),
                                contentDescription = "情侣头像",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        else -> {
                            Icon(
                                Icons.Rounded.AddAPhoto,
                                contentDescription = null,
                                tint = LocalMood.current.soft,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                }
                Text(
                    if (hasCover) "点击更换情侣头像" else "设置一张我们一起的照片",
                    color = LocalMood.current.stone,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (hasCover) {
                    TextButton(
                        onClick = {
                            draftCoverUri = null
                            clearCover = true
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = LocalMood.current.stone),
                    ) { Text("取消情侣头像") }
                }
            }

            SoftTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = "空间名称",
                placeholder = defaultSpaceName,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            LoverDateField(
                value = togetherDate.ifBlank {
                    java.time.LocalDate.now().minusYears(1).toString()
                },
                onValueChange = {
                    togetherDate = it
                    dateTouched = true
                },
                label = "在一起的那天",
                maxDate = java.time.LocalDate.now(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (initialTogetherDate.isNullOrBlank() && !dateTouched) {
                Text(
                    "尚未设置在一起的日子，选择日期后保存即可同步给双方",
                    color = LocalMood.current.stone,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = {
                    val dateToSave = when {
                        dateTouched -> togetherDate.trim().takeIf { it.isNotEmpty() }
                        !initialTogetherDate.isNullOrBlank() ->
                            togetherDate.trim().ifBlank { initialTogetherDate }
                        else -> null
                    }
                    onSave(
                        name.trim().ifBlank { "我们" },
                        dateToSave,
                        draftCoverUri,
                        clearCover && draftCoverUri == null,
                    )
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LocalMood.current.soft),
            ) { Text("保存") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneBindSheet(
    onDismiss: () -> Unit,
    onLookup: suspend (String) -> UserLookupResponse,
    onConfirm: (String) -> Unit,
) {
    var phone by rememberSaveable { mutableStateOf("") }
    var lookup by remember { mutableStateOf<UserLookupResponse?>(null) }
    var lookingUp by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(phone) {
        if (phone.length != 11) {
            lookup = null
            lookingUp = false
            return@LaunchedEffect
        }
        lookingUp = true
        delay(350)
        lookup = runCatching { onLookup(phone) }.getOrNull()
        lookingUp = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = LocalMood.current.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("绑定另一半", style = MaterialTheme.typography.headlineMedium)
            Text("输入对方已注册的手机号，等待对方同意后完成绑定", color = LocalMood.current.stone)
            SoftTextField(
                value = phone,
                onValueChange = { phone = it.filter(Char::isDigit).take(11) },
                label = "对方手机号",
                placeholder = "11 位手机号",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            when {
                phone.length < 11 -> Unit
                lookingUp -> {
                    Text("正在查找账号…", color = LocalMood.current.stone, style = MaterialTheme.typography.bodySmall)
                }
                lookup == null -> {
                    Text("查找失败，请稍后重试", color = LocalMood.current.stone, style = MaterialTheme.typography.bodySmall)
                }
                lookup?.found != true -> {
                    Text("没有对应账号", color = MaterialTheme.colorScheme.error)
                }
                lookup?.self == true -> {
                    Text("不能绑定自己的手机号", color = MaterialTheme.colorScheme.error)
                }
                else -> {
                    val nickname = lookup?.nickname.orEmpty()
                    val avatarUrl = lookup?.avatarUrl
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(LocalMood.current.softSurface)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        PersonAvatar(
                            nickname = nickname.ifBlank { "对方" },
                            avatarUrl = avatarUrl,
                            modifier = Modifier.size(48.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                nickname.ifBlank { "Lover 用户" },
                                fontWeight = FontWeight.SemiBold,
                                color = LocalMood.current.accent,
                            )
                            Text("已找到对方账号", color = LocalMood.current.stone, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Button(
                onClick = { onConfirm(phone) },
                enabled = phone.length == 11 && lookup?.found == true && lookup?.self != true,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LocalMood.current.soft),
            ) { Text("发送绑定请求") }
        }
    }
}

@Composable
private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(22.dp),
        color = LocalMood.current.softSurface,
        border = BorderStroke(1.dp, LocalMood.current.softOutline.copy(alpha = 0.65f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(LocalMood.current.blush, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = LocalMood.current.soft, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = LocalMood.current.stone)
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
        Icon(icon, null, tint = LocalMood.current.soft.copy(alpha = .55f), modifier = Modifier.size(58.dp))
        Spacer(Modifier.height(12.dp))
        Text(text, color = LocalMood.current.stone)
    }
}

private fun letterCapsuleLockHint(letter: Letter): String = when {
    letter.unlockOnPartnerBind -> "时间胶囊 · 绑定另一半后解锁"
    !letter.unlockAt.isNullOrBlank() -> "时间胶囊 · ${letter.unlockAt.take(10)} 解锁"
    else -> "时间胶囊 · 尚未解锁"
}

internal fun letterCapsuleLockDetail(letter: Letter): String = when {
    letter.unlockOnPartnerBind -> "这封时间胶囊将在绑定另一半以后自动开启。"
    !letter.unlockAt.isNullOrBlank() -> "这封时间胶囊将在 ${letter.unlockAt.take(10)} 解锁。"
    else -> "这封时间胶囊尚未解锁。"
}

@Composable
private fun LetterDetail(letter: Letter, currentUserId: String?, onDismiss: () -> Unit, onDelete: (() -> Unit)? = null) {
    val unlocked = letter.isUnlocked
    val mood = LocalMood.current
    val isAuthor = currentUserId != null && currentUserId == letter.senderId
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

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
                        onDismiss()
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
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = mood.background,
        icon = { Icon(if (unlocked) Icons.Rounded.Favorite else Icons.Rounded.Lock, null, tint = mood.soft) },
        title = { Text(letter.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (unlocked) letter.content.orEmpty()
                    else letterCapsuleLockDetail(letter),
                )
                HorizontalDivider()
                Text("${letter.senderNickname} · ${letter.createdAt.take(10)}", color = mood.stone)
                if (isAuthor) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFC45C5C)),
                    ) {
                        Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("删除此信件")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("收好") } },
    )
}
