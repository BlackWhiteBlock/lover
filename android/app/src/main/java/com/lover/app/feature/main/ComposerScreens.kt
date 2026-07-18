@file:OptIn(ExperimentalMaterial3Api::class)

package com.lover.app.feature.main

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import com.lover.app.core.media.PickGalleryImageAndVideoMultiple
import com.lover.app.core.media.PickGalleryImageOrVideo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lover.app.core.design.LocalMood
import com.lover.app.core.design.LoverDateField
import com.lover.app.core.design.SoftTextField
import com.lover.app.core.data.AppRepository
import com.lover.app.core.media.MediaTakenDateReader
import com.lover.app.core.media.isLocalVideoUri
import com.lover.app.core.model.AnniversaryType
import com.lover.app.core.model.LetterType
import com.lover.app.core.model.MediaAssetPart
import com.lover.app.core.model.MediaItem
import com.lover.app.core.model.MediaType
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ComposerHost(
    editor: Editor,
    initialMediaUris: List<Uri>,
    linked: Boolean,
    onDismiss: () -> Unit,
    onSaveMedia: (List<Uri>, String, String) -> Unit,
    onSaveAnniversary: (String, String, AnniversaryType) -> Unit,
    onSaveLetter: (String, String, LetterType, String?, Boolean) -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(LocalMood.current.background),
    ) {
        when (editor) {
            Editor.MEDIA -> MediaComposeScreen(
                initialUris = initialMediaUris,
                onClose = onDismiss,
                onSave = onSaveMedia,
            )
            Editor.ANNIVERSARY -> AnniversaryComposeScreen(
                onClose = onDismiss,
                onSave = onSaveAnniversary,
            )
            Editor.LETTER -> LetterComposeScreen(
                linked = linked,
                onClose = onDismiss,
                onSave = onSaveLetter,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposerScaffold(
    title: String,
    subtitle: String,
    onClose: () -> Unit,
    actionEnabled: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    showTopAction: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    val mood = LocalMood.current
    Scaffold(
        containerColor = mood.background,
        topBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(mood.blush.copy(alpha = 0.85f), mood.background)),
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = mood.accent)
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, color = mood.accent)
                        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = mood.stone)
                    }
                    if (showTopAction) {
                        TextButton(
                            onClick = onAction,
                            enabled = actionEnabled,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = mood.soft,
                                disabledContentColor = mood.stone.copy(alpha = 0.4f),
                            ),
                        ) {
                            Text(actionLabel, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Spacer(Modifier.width(48.dp))
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                color = mood.background.copy(alpha = 0.96f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Button(
                    onClick = onAction,
                    enabled = actionEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = mood.soft,
                        disabledContainerColor = mood.softOutline,
                    ),
                ) {
                    Text(actionLabel)
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(mood.background, mood.softSurface.copy(alpha = 0.55f)))),
        ) {
            content(padding)
        }
    }
}

@Composable
private fun SoftEditorChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
) {
    val mood = LocalMood.current
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(18.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = mood.blush,
            selectedLabelColor = mood.soft,
            containerColor = Color.White,
            labelColor = mood.stone,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = mood.softOutline,
            selectedBorderColor = LocalMood.current.soft.copy(alpha = 0.35f),
        ),
    )
}

@Composable
fun MediaComposeScreen(
    initialUris: List<Uri>,
    onClose: () -> Unit,
    onSave: (List<Uri>, String, String) -> Unit,
) {
    var caption by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var uris by remember { mutableStateOf(initialUris) }
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val remaining = (ComposerMaxMediaPick - uris.size).coerceAtLeast(0)

    fun prefillDateFromFirst(selected: List<Uri>) {
        val first = selected.firstOrNull() ?: return
        scope.launch {
            val taken = withContext(Dispatchers.IO) {
                MediaTakenDateReader.readLocalDate(context, first)
            } ?: return@launch
            date = taken.toString()
        }
    }

    fun mergePicked(more: List<Uri>) {
        if (more.isEmpty()) return
        val wasEmpty = uris.isEmpty()
        val next = (uris + more).distinctBy { it.toString() }.take(ComposerMaxMediaPick)
        uris = next
        if (wasEmpty) prefillDateFromFirst(next)
    }

    val pickMultiple = rememberLauncherForActivityResult(
        PickGalleryImageAndVideoMultiple(ComposerMaxMediaPick),
    ) { more -> mergePicked(more) }
    val pickSingle = rememberLauncherForActivityResult(PickGalleryImageOrVideo()) { uri ->
        if (uri != null) mergePicked(listOf(uri))
    }
    fun launchPick() {
        if (remaining <= 0) return
        if (remaining == 1) pickSingle.launch(Unit) else pickMultiple.launch(Unit)
    }

    val mood = LocalMood.current
    ComposerScaffold(
        title = if (mood.solo) "存下这一刻的时光" else "存下这些属于我们的时光",
        subtitle = if (uris.isEmpty()) {
            "添加照片或视频"
        } else {
            "已选 ${uris.size} 项 · 点击预览 · 长按排序"
        },
        onClose = onClose,
        actionEnabled = uris.isNotEmpty(),
        actionLabel = "保存",
        onAction = { onSave(uris, caption, date) },
        showTopAction = false,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (uris.isEmpty()) {
                Surface(
                    onClick = ::launchPick,
                    shape = RoundedCornerShape(28.dp),
                    color = LocalMood.current.softSurface,
                    border = BorderStroke(1.dp, LocalMood.current.softOutline),
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(56.dp).background(LocalMood.current.blush, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.AddPhotoAlternate, null, tint = LocalMood.current.soft, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("添加照片或视频", fontWeight = FontWeight.SemiBold, color = LocalMood.current.accent)
                        Text("最多 $ComposerMaxMediaPick 项，支持混选", style = MaterialTheme.typography.bodySmall, color = LocalMood.current.stone)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReorderableMediaDraftGrid(
                        cells = uris.map { MediaDraftCell.Local(it) },
                        canReorder = uris.size > 1,
                        onReorder = { from, to ->
                            uris = uris.toMutableList().apply { add(to, removeAt(from)) }
                        },
                        onPreview = { previewIndex = it },
                        canRemoveAt = { true },
                        onRemoveAt = { index ->
                            uris = uris.toMutableList().also { it.removeAt(index) }
                        },
                    )
                    Text(
                        "长按拖动可调整顺序",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalMood.current.stone,
                    )
                }
                if (remaining > 0) {
                    TextButton(
                        onClick = ::launchPick,
                        colors = ButtonDefaults.textButtonColors(contentColor = LocalMood.current.soft),
                    ) {
                        Icon(Icons.Rounded.AddPhotoAlternate, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("继续添加（还可 $remaining 项）")
                    }
                }
            }

            SoftTextField(
                value = caption,
                onValueChange = { caption = it.take(200) },
                label = "这一刻想说…",
                placeholder = "写给这段时光的一句话",
                singleLine = false,
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            LoverDateField(
                value = date,
                onValueChange = { date = it },
                label = "记录日期",
                maxDate = LocalDate.now(),
                modifier = Modifier.fillMaxWidth(),
                supportingText = "优先使用第一张照片的拍摄日期，可手动修改",
            )
        }
    }

    previewIndex?.let { index ->
        val assets = uris.mapIndexed { i, uri ->
            val isVideo = isLocalVideoUri(context, uri)
            MediaAssetPart(
                id = "local-$i",
                type = if (isVideo) MediaType.VIDEO else MediaType.IMAGE,
                assetId = "local-$i",
                url = uri.toString(),
                sortOrder = i,
            )
        }
        MediaPreviewDialog(
            assets = assets,
            initialIndex = index.coerceIn(0, assets.lastIndex),
            caption = caption,
            mediaDate = date,
            onDismiss = { previewIndex = null },
        )
    }
}

@Composable
fun MediaEditScreen(
    item: MediaItem,
    currentUserId: String?,
    onClose: () -> Unit,
    onSave: (
        caption: String,
        date: String?,
        assetOrder: List<AppRepository.MediaEditOrderItem>?,
    ) -> Unit,
    onDelete: () -> Unit,
) {
    val isOwner = !currentUserId.isNullOrBlank() && item.uploaderId == currentUserId
    var caption by rememberSaveable(item.id) { mutableStateOf(item.caption) }
    var date by rememberSaveable(item.id) { mutableStateOf(item.mediaDate) }
    val initialCells = remember(item.id) {
        item.assets.sortedBy { it.sortOrder }.map { MediaDraftCell.Remote(it) as MediaDraftCell }
    }
    var cells by remember(item.id) { mutableStateOf(initialCells) }
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val totalCount = cells.size
    val remaining = (ComposerMaxMediaPick - totalCount).coerceAtLeast(0)
    val mediaDirty = cells.map { it.key } != initialCells.map { it.key }
    val dirty = caption.trim() != item.caption.trim()
        || (isOwner && date != item.mediaDate)
        || mediaDirty

    val pickMultiple = rememberLauncherForActivityResult(
        PickGalleryImageAndVideoMultiple(ComposerMaxMediaPick),
    ) { more ->
        if (more.isEmpty() || !isOwner) return@rememberLauncherForActivityResult
        val room = (ComposerMaxMediaPick - cells.size).coerceAtLeast(0)
        val extras = more.distinctBy { it.toString() }.take(room).map { MediaDraftCell.Local(it) }
        cells = cells + extras
    }
    val pickSingle = rememberLauncherForActivityResult(PickGalleryImageOrVideo()) { uri ->
        if (uri != null && remaining > 0 && isOwner) {
            cells = cells + MediaDraftCell.Local(uri)
        }
    }
    fun openPicker() {
        if (!isOwner || remaining <= 0) return
        if (remaining == 1) pickSingle.launch(Unit) else pickMultiple.launch(Unit)
    }

    ComposerScaffold(
        title = "编辑时光",
        subtitle = if (isOwner) "可修改描述与媒体 · 长按拖动排序" else "仅可修改文字描述",
        onClose = onClose,
        actionEnabled = dirty && totalCount > 0,
        actionLabel = "保存",
        onAction = {
            val order = if (isOwner && mediaDirty) {
                cells.map { cell ->
                    when (cell) {
                        is MediaDraftCell.Remote ->
                            AppRepository.MediaEditOrderItem.Existing(cell.part.id)
                        is MediaDraftCell.Local ->
                            AppRepository.MediaEditOrderItem.New(cell.uri)
                    }
                }
            } else {
                null
            }
            onSave(
                caption,
                if (isOwner && date != item.mediaDate) date else null,
                order,
            )
        },
        showTopAction = false,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (totalCount == 0) {
                Text("至少需要保留一张照片或视频", color = MaterialTheme.colorScheme.error)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReorderableMediaDraftGrid(
                        cells = cells,
                        canReorder = isOwner && cells.size > 1,
                        onReorder = { from, to ->
                            cells = cells.toMutableList().apply { add(to, removeAt(from)) }
                        },
                        onPreview = { previewIndex = it },
                        canRemoveAt = { index ->
                            when (val cell = cells.getOrNull(index)) {
                                is MediaDraftCell.Remote -> isOwner && totalCount > 1
                                is MediaDraftCell.Local -> true
                                null -> false
                            }
                        },
                        onRemoveAt = { index ->
                            cells = cells.toMutableList().also { it.removeAt(index) }
                        },
                    )
                    if (isOwner && cells.size > 1) {
                        Text(
                            "长按拖动可调整顺序",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalMood.current.stone,
                        )
                    }
                }
            }
            if (isOwner && remaining > 0) {
                TextButton(
                    onClick = ::openPicker,
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalMood.current.soft),
                ) {
                    Icon(Icons.Rounded.AddPhotoAlternate, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("继续添加（还可 $remaining 项）")
                }
            }

            SoftTextField(
                value = caption,
                onValueChange = { caption = it.take(200) },
                label = "这一刻想说…",
                placeholder = "写给这段时光的一句话",
                singleLine = false,
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            if (isOwner) {
                LoverDateField(
                    value = date,
                    onValueChange = { date = it },
                    label = "记录日期",
                    maxDate = LocalDate.now(),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = "仅创建者可修改记录日期",
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = LocalMood.current.softSurface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("记录日期", color = LocalMood.current.stone)
                        Text(date, color = LocalMood.current.accent, fontWeight = FontWeight.Medium)
                    }
                }
            }

            if (isOwner) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("删除这段时光")
                }
            }
        }
    }

    previewIndex?.let { index ->
        val previewAssets = cells.mapIndexed { i, cell ->
            when (cell) {
                is MediaDraftCell.Remote -> cell.part.copy(
                    url = cell.part.url.ifBlank { cell.part.previewUrl },
                    sortOrder = i,
                )
                is MediaDraftCell.Local -> {
                    val isVideo = isLocalVideoUri(context, cell.uri)
                    MediaAssetPart(
                        id = "new-$i",
                        type = if (isVideo) MediaType.VIDEO else MediaType.IMAGE,
                        assetId = "new-$i",
                        url = cell.uri.toString(),
                        sortOrder = i,
                    )
                }
            }
        }
        MediaPreviewDialog(
            assets = previewAssets,
            initialIndex = index.coerceIn(0, previewAssets.lastIndex.coerceAtLeast(0)),
            caption = caption,
            mediaDate = date,
            onDismiss = { previewIndex = null },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = LocalMood.current.background,
            title = { Text("删除这段时光？") },
            text = { Text("将删除整条时光及其中的全部照片/视频，确认删除吗？", color = LocalMood.current.stone) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDelete = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = LocalMood.current.stone,
                    ),
                ) { Text("取消") }
            },
        )
    }
}

@Composable
fun AnniversaryComposeScreen(
    onClose: () -> Unit,
    onSave: (String, String, AnniversaryType) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().plusMonths(1).toString()) }
    var type by rememberSaveable { mutableStateOf(AnniversaryType.YEARLY) }

    val mood = LocalMood.current
    ComposerScaffold(
        title = "新建纪念日",
        subtitle = if (mood.solo) "把重要的日子好好放进日历" else "把重要的日子好好放进我们的日历",
        onClose = onClose,
        actionEnabled = title.isNotBlank(),
        actionLabel = "保存",
        onAction = { onSave(title, date, type) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = mood.blush.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        Modifier.size(52.dp).background(Color.White.copy(alpha = 0.7f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Favorite, null, tint = mood.soft)
                    }
                    Column {
                        Text("纪念类型", fontWeight = FontWeight.SemiBold, color = mood.accent)
                        Text("年度会循环倒数，里程碑指向那天", style = MaterialTheme.typography.bodySmall, color = LocalMood.current.stone)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftEditorChip(type == AnniversaryType.YEARLY, { type = AnniversaryType.YEARLY }, "年度纪念")
                SoftEditorChip(type == AnniversaryType.MILESTONE, { type = AnniversaryType.MILESTONE }, "里程碑")
            }
            SoftTextField(
                value = title,
                onValueChange = { title = it.take(30) },
                label = "纪念日名称",
                placeholder = "例如：正式在一起",
                modifier = Modifier.fillMaxWidth(),
            )
            LoverDateField(
                value = date,
                onValueChange = { date = it },
                label = "纪念日期",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun AnniversaryEditScreen(
    item: com.lover.app.core.model.Anniversary,
    onClose: () -> Unit,
    onSave: (String, String, AnniversaryType) -> Unit,
    onDelete: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(item.title) }
    var date by rememberSaveable { mutableStateOf(item.date) }
    var type by rememberSaveable { mutableStateOf(item.type) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val mood = LocalMood.current
    ComposerScaffold(
        title = "编辑纪念日",
        subtitle = "修改这个重要的日子",
        onClose = onClose,
        actionEnabled = title.isNotBlank(),
        actionLabel = "保存",
        onAction = { onSave(title, date, type) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = mood.blush.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        Modifier.size(52.dp).background(Color.White.copy(alpha = 0.7f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Favorite, null, tint = mood.soft)
                    }
                    Column {
                        Text("纪念类型", fontWeight = FontWeight.SemiBold, color = mood.accent)
                        Text("年度会循环倒数，里程碑指向那天", style = MaterialTheme.typography.bodySmall, color = LocalMood.current.stone)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftEditorChip(type == AnniversaryType.YEARLY, { type = AnniversaryType.YEARLY }, "年度纪念")
                SoftEditorChip(type == AnniversaryType.MILESTONE, { type = AnniversaryType.MILESTONE }, "里程碑")
            }
            SoftTextField(
                value = title,
                onValueChange = { title = it.take(30) },
                label = "纪念日名称",
                placeholder = "例如：正式在一起",
                modifier = Modifier.fillMaxWidth(),
            )
            LoverDateField(
                value = date,
                onValueChange = { date = it },
                label = "纪念日期",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            if (confirmDelete) {
                AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    shape = RoundedCornerShape(28.dp),
                    containerColor = mood.background,
                    title = { Text("确认删除") },
                    text = { Text("删除后无法恢复，确定要删除「${item.title}」吗？") },
                    confirmButton = {
                        TextButton(
                            onClick = onDelete,
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
            } else {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, Color(0xFFC45C5C).copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC45C5C)),
                ) {
                    Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("删除此纪念日")
                }
            }
        }
    }
}

@Composable
fun LetterComposeScreen(
    linked: Boolean,
    onClose: () -> Unit,
    onSave: (String, String, LetterType, String?, Boolean) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable {
        mutableStateOf(if (linked) LetterType.INSTANT else LetterType.CAPSULE)
    }
    var unlockDate by rememberSaveable { mutableStateOf(LocalDate.now().plusMonths(1).toString()) }
    var unlockOnPartnerBind by rememberSaveable { mutableStateOf(false) }

    ComposerScaffold(
        title = "写给 TA",
        subtitle = if (type == LetterType.INSTANT) "即刻送达的一封情书" else "封存到未来的时间胶囊",
        onClose = onClose,
        actionEnabled = title.isNotBlank() && content.isNotBlank(),
        actionLabel = if (type == LetterType.CAPSULE) "封存" else "寄出",
        onAction = {
            onSave(
                title,
                content,
                type,
                unlockDate.takeIf { type == LetterType.CAPSULE && !unlockOnPartnerBind },
                type == LetterType.CAPSULE && unlockOnPartnerBind,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftEditorChip(
                    selected = type == LetterType.INSTANT,
                    onClick = { if (linked) type = LetterType.INSTANT },
                    label = "即时信",
                )
                SoftEditorChip(type == LetterType.CAPSULE, { type = LetterType.CAPSULE }, "时间胶囊")
            }
            if (!linked) {
                Text("未绑定另一半时，只能写时间胶囊", color = LocalMood.current.stone, style = MaterialTheme.typography.bodySmall)
            }
            SoftTextField(
                value = title,
                onValueChange = { title = it.take(40) },
                label = "标题",
                placeholder = "给这封信起个名字",
                modifier = Modifier.fillMaxWidth(),
            )
            SoftTextField(
                value = content,
                onValueChange = { content = it },
                label = "想对 TA 说的话",
                placeholder = "写下此刻的心意…",
                singleLine = false,
                minLines = 10,
                modifier = Modifier.fillMaxWidth(),
            )
            if (type == LetterType.CAPSULE) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = unlockOnPartnerBind,
                        onCheckedChange = { unlockOnPartnerBind = it },
                        colors = androidx.compose.material3.CheckboxDefaults.colors(
                            checkedColor = LocalMood.current.soft,
                        ),
                    )
                    Text("在绑定另一半以后，自动开启", color = LocalMood.current.stone)
                }
                if (!unlockOnPartnerBind) {
                    LoverDateField(
                        value = unlockDate,
                        onValueChange = { unlockDate = it },
                        label = "解锁日期",
                        minDate = LocalDate.now().plusDays(1),
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = "到期前双方都无法阅读正文",
                    )
                }
            }
        }
    }
}
