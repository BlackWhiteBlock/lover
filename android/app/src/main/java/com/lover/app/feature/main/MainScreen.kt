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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.lover.app.core.design.*
import com.lover.app.core.media.PickGalleryImage
import com.lover.app.core.media.listMediaImageRequest
import com.lover.app.core.media.signedMediaImageRequest
import com.lover.app.core.model.*

internal const val MaxMediaPick = 20

enum class Editor { MEDIA, ANNIVERSARY, LETTER }

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.data.collectAsState()
    val tab by viewModel.selectedTab.collectAsState()
    val pendingUploads by viewModel.pendingMediaUploads.collectAsState()
    var editor by remember { mutableStateOf<Editor?>(null) }
    var mediaDetail by remember { mutableStateOf<MediaItem?>(null) }
    var mediaEdit by remember { mutableStateOf<MediaItem?>(null) }
    var letterDetail by remember { mutableStateOf<Letter?>(null) }
    // 按用户区分；不持久化，避免换号/重新登录后弹窗被永久抑制
    var postponedBindIds by remember(state.user?.id) { mutableStateOf(listOf<String>()) }
    val composing = editor != null
    val mediaDetailOpen = mediaDetail != null
    val mediaEditOpen = mediaEdit != null
    val overlayOpen = composing || mediaDetailOpen || mediaEditOpen
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
                            onMedia = { item ->
                                viewModel.openMediaDetail(item) { mediaDetail = it }
                            },
                            onCapture = { editor = Editor.MEDIA },
                            onWrite = {
                                viewModel.selectTab(MainTab.LETTERS)
                                editor = Editor.LETTER
                            },
                        )
                        MainTab.TIMELINE -> TimelinePage(
                            media = state.media,
                            pendingUploads = pendingUploads,
                            onMedia = { item -> viewModel.openMediaDetail(item) { mediaDetail = it } },
                        )
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
                        .background(WarmBackground),
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
        Column(modifier = Modifier.weight(1f)) {
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
private fun TimelinePage(
    media: List<MediaItem>,
    pendingUploads: List<PendingMediaUpload>,
    onMedia: (MediaItem) -> Unit,
) {
    Column {
        PageHeader("相爱时光", "Visual Memories")
        if (media.isEmpty() && pendingUploads.isEmpty()) {
            EmptyHint("选择照片或视频，记录共同的故事", Icons.Rounded.PhotoLibrary)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    pendingUploads,
                    key = { "pending-${it.id}" },
                    span = { GridItemSpan(1) },
                ) { pending ->
                    UploadingMediaCard(pending)
                }
                if (media.isNotEmpty()) {
                    val featured = media.first()
                    item(
                        key = "featured-${featured.id}",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        FeaturedMediaCard(
                            item = featured,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3f)
                                .clickable { onMedia(featured) },
                        )
                    }
                    items(
                        media.drop(1),
                        key = { it.id },
                        span = { GridItemSpan(1) },
                    ) {
                        MediaImage(it, Modifier.aspectRatio(1f).clickable { onMedia(it) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedMediaCard(
    item: MediaItem,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val cover = item.cover
    val thumbUrl = item.thumbnailUrl ?: item.url
    Box(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Blush),
    ) {
        if (!thumbUrl.isNullOrBlank() && cover != null) {
            AsyncImage(
                model = listMediaImageRequest(context, thumbUrl, cover.assetId),
                contentDescription = item.caption,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.55f),
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "精选",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 2.sp,
            )
            Text(
                item.caption.ifBlank { item.mediaDate },
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.assetCount > 1) {
            Text(
                "${item.assetCount}",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        if (item.type == MediaType.VIDEO && item.assetCount <= 1) {
            Icon(
                Icons.Rounded.PlayCircle,
                "视频",
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(52.dp),
            )
        }
    }
}

@Composable
private fun UploadingMediaCard(pending: PendingMediaUpload) {
    Box(
        Modifier
            .aspectRatio(.8f)
            .clip(RoundedCornerShape(26.dp))
            .background(Blush),
    ) {
        AsyncImage(
            model = pending.previewUri,
            contentDescription = pending.caption,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f)),
        )
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "正在上传",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            LinearProgressIndicator(
                progress = { pending.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Rose,
                trackColor = Color.White.copy(alpha = 0.35f),
            )
            Text(
                "${pending.completed}/${pending.total}",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall,
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
    Box(modifier.clip(RoundedCornerShape(26.dp)).background(Blush)) {
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
    var showEditCard by rememberSaveable { mutableStateOf(false) }
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
                val spaceName = state.couple?.name?.takeIf { it.isNotBlank() } ?: "我们的小宇宙"
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
                initialName = state.couple?.name?.takeIf { it.isNotBlank() } ?: "我们的小宇宙",
                currentAvatarUrl = state.user?.avatarUrl,
                onDismiss = { showEditCard = false },
                onSave = { name, avatarUri ->
                    viewModel.updatePersonalCard(name, avatarUri)
                    showEditCard = false
                },
            )
        }
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
        colors = CardDefaults.cardColors(containerColor = Blush.copy(alpha = 0.85f)),
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
                    tint = DeepRose.copy(alpha = 0.75f),
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
        colors = CardDefaults.cardColors(containerColor = Blush.copy(alpha = 0.85f)),
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
                    tint = DeepRose.copy(alpha = 0.75f),
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
        colors = CardDefaults.cardColors(containerColor = Blush.copy(alpha = 0.85f)),
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
                    tint = DeepRose.copy(alpha = 0.75f),
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
                    color = Stone,
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
                    color = Stone,
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
                .background(Rose.copy(alpha = 0.9f)),
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
                    val context = LocalContext.current
                    AsyncImage(
                        model = signedMediaImageRequest(context, avatarUrl),
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
private fun PersonalCardEditSheet(
    initialName: String,
    currentAvatarUrl: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, avatarUri: Uri?) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var draftAvatarUri by remember { mutableStateOf<Uri?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pickAvatar = rememberLauncherForActivityResult(PickGalleryImage()) { uri ->
        if (uri != null) draftAvatarUri = uri
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = WarmBackground,
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
            Text("可以更换头像和空间名称，绑定后双方还会有共同的空间设置", color = Stone)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .background(Blush)
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
                                tint = Rose,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                }
                Text(
                    "点击更换头像",
                    color = Stone,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable { pickAvatar.launch(Unit) },
                )
            }

            SoftTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = "空间名称",
                placeholder = "我们的小宇宙",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    onSave(name.trim().ifBlank { "我们的小宇宙" }, draftAvatarUri)
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Rose),
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
        containerColor = WarmBackground,
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
            Text("情侣头像仅影响你这边的展示；空间名称与在一起的日子会同步给双方", color = Stone)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .background(Blush)
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
                                tint = Rose,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                }
                Text(
                    if (hasCover) "点击更换情侣头像" else "设置一张我们一起的照片",
                    color = Stone,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (hasCover) {
                    TextButton(
                        onClick = {
                            draftCoverUri = null
                            clearCover = true
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Stone),
                    ) { Text("取消情侣头像") }
                }
            }

            SoftTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = "空间名称",
                placeholder = "我们的小宇宙",
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
                    color = Stone,
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
                colors = ButtonDefaults.buttonColors(containerColor = Rose),
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
            when {
                phone.length < 11 -> Unit
                lookingUp -> {
                    Text("正在查找账号…", color = Stone, style = MaterialTheme.typography.bodySmall)
                }
                lookup == null -> {
                    Text("查找失败，请稍后重试", color = Stone, style = MaterialTheme.typography.bodySmall)
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
                            .background(SoftSurface)
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
                                color = DeepRose,
                            )
                            Text("已找到对方账号", color = Stone, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Button(
                onClick = { onConfirm(phone) },
                enabled = phone.length == 11 && lookup?.found == true && lookup?.self != true,
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
