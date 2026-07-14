@file:OptIn(ExperimentalMaterial3Api::class)

package com.lover.app.feature.main

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.lover.app.core.design.*
import com.lover.app.core.model.*
import java.time.LocalDate

private enum class Editor { MEDIA, ANNIVERSARY, LETTER }

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.data.collectAsState()
    val tab by viewModel.selectedTab.collectAsState()
    val message by viewModel.message.collectAsState()
    var editor by remember { mutableStateOf<Editor?>(null) }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var mediaDetail by remember { mutableStateOf<MediaItem?>(null) }
    var letterDetail by remember { mutableStateOf<Letter?>(null) }
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            pickedUri = uri
            editor = Editor.MEDIA
        }
    }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = WarmBackground,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { LoverNavigation(tab, viewModel::selectTab) },
        floatingActionButton = {
            val target = when (tab) {
                MainTab.TIMELINE -> Editor.MEDIA
                MainTab.ANNIVERSARY -> Editor.ANNIVERSARY
                MainTab.LETTERS -> Editor.LETTER
                else -> null
            }
            if (target != null) {
                FloatingActionButton(
                    onClick = {
                        if (target == Editor.MEDIA) {
                            picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                        } else editor = target
                    },
                    containerColor = Rose,
                ) { Icon(Icons.Rounded.Add, "新增", tint = Color.White) }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = tab,
            transitionSpec = { scaleIn(initialScale = .98f) togetherWith scaleOut(targetScale = 1.02f) },
            label = "tab",
            modifier = Modifier.padding(padding),
        ) { current ->
            Box(Modifier.fillMaxSize().widthIn(max = 600.dp)) {
                when (current) {
                    MainTab.HOME -> HomePage(
                        state,
                        onMedia = { mediaDetail = it },
                        onCapture = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
                        onWrite = { viewModel.selectTab(MainTab.LETTERS); editor = Editor.LETTER },
                    )
                    MainTab.TIMELINE -> TimelinePage(state.media, { mediaDetail = it }) {
                        picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
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

    if (editor == Editor.MEDIA && pickedUri != null) {
        MediaEditor(
            uri = pickedUri!!,
            onDismiss = { editor = null; pickedUri = null },
            onSave = { caption, date ->
                viewModel.addMedia(
                    pickedUri!!,
                    caption,
                    date,
                )
                editor = null
                pickedUri = null
            },
        )
    }
    if (editor == Editor.ANNIVERSARY) {
        AnniversaryEditor(
            onDismiss = { editor = null },
            onSave = { title, date, type ->
                viewModel.addAnniversary(title, date, type)
                editor = null
            },
        )
    }
    if (editor == Editor.LETTER) {
        LetterEditor(
            onDismiss = { editor = null },
            onSave = { title, content, type, date ->
                viewModel.addLetter(title, content, type, date)
                editor = null
            },
        )
    }
    mediaDetail?.let { MediaDetail(it) { mediaDetail = null } }
    letterDetail?.let { LetterDetail(it) { letterDetail = null } }
}

@Composable
private fun LoverNavigation(selected: MainTab, onSelect: (MainTab) -> Unit) {
    val tabs = listOf(
        Triple(MainTab.HOME, "首页", Icons.Rounded.Favorite),
        Triple(MainTab.TIMELINE, "时光", Icons.Rounded.PhotoLibrary),
        Triple(MainTab.ANNIVERSARY, "纪念", Icons.Rounded.Event),
        Triple(MainTab.LETTERS, "信封", Icons.Rounded.Mail),
        Triple(MainTab.PROFILE, "我们", Icons.Rounded.People),
    )
    Surface(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        shape = CircleShape,
        shadowElevation = 12.dp,
        color = Color.White.copy(alpha = .97f),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 4.dp)) {
            tabs.forEach { (tab, label, icon) ->
                val scale by animateFloatAsState(if (selected == tab) 1.12f else 1f, label = "icon")
                NavigationBarItem(
                    selected = selected == tab,
                    onClick = { onSelect(tab) },
                    icon = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(icon, label, Modifier.scale(scale))
                            if (selected == tab) {
                                Box(Modifier.padding(top = 2.dp).size(4.dp).background(Rose, CircleShape))
                            }
                        }
                    },
                    label = { Text(label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Rose,
                        selectedTextColor = Rose,
                        indicatorColor = Blush,
                        unselectedIconColor = Stone,
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
                colors = CardDefaults.cardColors(containerColor = Blush),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(listOf(Blush, Peach.copy(alpha = .75f))))
                        .padding(30.dp),
                ) {
                    Column {
                        Text("Loving Journey", color = DeepRose)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$days", style = MaterialTheme.typography.displayLarge)
                            Text(" 天", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
                        }
                        Text("每一天，都算数", color = Stone)
                    }
                    Icon(
                        Icons.Rounded.Favorite,
                        null,
                        tint = Rose.copy(alpha = .32f),
                        modifier = Modifier.align(Alignment.CenterEnd).size(100.dp),
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
    Card(modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp)) {
            Icon(icon, null, tint = Rose)
            Spacer(Modifier.height(16.dp))
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
        Text(
            "当前版本先支持图片上传；视频需要封面资产，暂不开放。",
            style = MaterialTheme.typography.bodySmall,
            color = Stone,
            modifier = Modifier.padding(horizontal = 22.dp),
        )
        Spacer(Modifier.height(8.dp))
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
    Box(modifier.clip(MaterialTheme.shapes.medium).background(Blush)) {
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
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
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
                OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, null)
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
                    colors = CardDefaults.cardColors(containerColor = if (unlocked) Color.White else Blush),
                ) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            if (unlocked) Icons.Rounded.Drafts else Icons.Rounded.Lock,
                            null,
                            tint = Rose,
                            modifier = Modifier.size(32.dp),
                        )
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
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
            Card(colors = CardDefaults.cardColors(containerColor = Blush), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row {
                        Avatar(state.user?.nickname?.take(1) ?: "我")
                        Avatar(partner?.nickname?.take(1) ?: "TA", Modifier.offset(x = (-12).dp))
                    }
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
                OutlinedButton(onClick = { confirm = "request" }, modifier = Modifier.fillMaxWidth()) {
                    Text("申请解除情侣绑定")
                }
            } else if (pending.requestedBy == state.user?.id) {
                Text("解绑申请等待伴侣确认", color = Stone)
                OutlinedButton(onClick = { confirm = "cancel" }, modifier = Modifier.fillMaxWidth()) {
                    Text("取消解绑申请")
                }
            } else {
                Text("伴侣发起了解绑申请", color = MaterialTheme.colorScheme.error)
                Button(onClick = { confirm = "confirm" }, modifier = Modifier.fillMaxWidth()) {
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
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("申请提交后，需要伴侣确认才会正式解绑。")
                        OutlinedTextField(
                            reason,
                            { reason = it.take(300) },
                            label = { Text("原因（可选）") },
                        )
                    }
                } else {
                    Text(
                        if (action == "confirm") "确认后情侣空间将解散，请谨慎操作。"
                        else if (action == "logout") "退出后可再次使用手机号登录。"
                        else "该待处理申请将被取消。",
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when (action) {
                        "request" -> onRequestUnbinding(reason)
                        "confirm" -> pending?.id?.let(onConfirmUnbinding)
                        "cancel" -> pending?.id?.let(onCancelUnbinding)
                        else -> onLogout()
                    }
                    confirm = null
                }) { Text("确认") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun Avatar(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier.size(72.dp).background(Rose, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White, style = MaterialTheme.typography.headlineMedium) }
}

@Composable
private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Rose)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Stone)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = Stone)
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
private fun MediaEditor(uri: Uri, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var caption by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("存下这个瞬间") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(uri, null, Modifier.fillMaxWidth().height(220.dp).clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop)
                OutlinedTextField(caption, { caption = it.take(200) }, label = { Text("这一刻想说…") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(date, { date = it.take(10) }, label = { Text("日期 YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(caption, date) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AnniversaryEditor(onDismiss: () -> Unit, onSave: (String, String, AnniversaryType) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().plusMonths(1).toString()) }
    var type by rememberSaveable { mutableStateOf(AnniversaryType.YEARLY) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建纪念日") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it.take(30) }, label = { Text("纪念日名称") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(date, { date = it.take(10) }, label = { Text("日期 YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(type == AnniversaryType.YEARLY, { type = AnniversaryType.YEARLY }, { Text("年度纪念") })
                    FilterChip(type == AnniversaryType.MILESTONE, { type = AnniversaryType.MILESTONE }, { Text("里程碑") })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, date, type) }, enabled = title.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun LetterEditor(onDismiss: () -> Unit, onSave: (String, String, LetterType, String?) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(LetterType.INSTANT) }
    var unlockDate by rememberSaveable { mutableStateOf(LocalDate.now().plusMonths(1).toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("写给 TA") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(type == LetterType.INSTANT, { type = LetterType.INSTANT }, { Text("即时信") })
                    FilterChip(type == LetterType.CAPSULE, { type = LetterType.CAPSULE }, { Text("时间胶囊") })
                }
                OutlinedTextField(title, { title = it }, label = { Text("标题") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    content,
                    { content = it },
                    label = { Text("想对 TA 说的话") },
                    minLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (type == LetterType.CAPSULE) {
                    OutlinedTextField(unlockDate, { unlockDate = it.take(10) }, label = { Text("解锁日期 YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, content, type, unlockDate.takeIf { type == LetterType.CAPSULE }) },
                enabled = title.isNotBlank() && content.isNotBlank(),
            ) { Text("封存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun MediaDetail(item: MediaItem, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                item.url,
                item.caption,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
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
                if (item.type == MediaType.VIDEO) Text("视频预览（MVP 展示封面）", color = Rose)
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
