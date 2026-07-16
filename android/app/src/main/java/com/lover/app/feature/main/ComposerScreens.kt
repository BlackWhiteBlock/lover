@file:OptIn(ExperimentalMaterial3Api::class)

package com.lover.app.feature.main

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
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
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.lover.app.core.design.Blush
import com.lover.app.core.design.DeepRose
import com.lover.app.core.design.LoverDateField
import com.lover.app.core.design.Peach
import com.lover.app.core.design.Rose
import com.lover.app.core.design.SoftOutline
import com.lover.app.core.design.SoftSurface
import com.lover.app.core.design.SoftTextField
import com.lover.app.core.design.Stone
import com.lover.app.core.design.WarmBackground
import com.lover.app.core.media.MediaTakenDateReader
import com.lover.app.core.model.AnniversaryType
import com.lover.app.core.model.LetterType
import com.lover.app.core.model.MediaAssetPart
import com.lover.app.core.model.MediaType
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ComposerMaxMediaPick = 9

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
            .background(WarmBackground),
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
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Blush.copy(alpha = 0.85f), WarmBackground)),
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", tint = DeepRose)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(title, style = MaterialTheme.typography.titleLarge, color = DeepRose)
                        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Stone)
                    }
                    if (showTopAction) {
                        TextButton(
                            onClick = onAction,
                            enabled = actionEnabled,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Rose,
                                disabledContentColor = Stone.copy(alpha = 0.4f),
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
                color = WarmBackground.copy(alpha = 0.96f),
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
                        containerColor = Rose,
                        disabledContainerColor = SoftOutline,
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
                .background(Brush.verticalGradient(listOf(WarmBackground, SoftSurface.copy(alpha = 0.55f)))),
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
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(18.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Blush,
            selectedLabelColor = Rose,
            containerColor = Color.White,
            labelColor = Stone,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = SoftOutline,
            selectedBorderColor = Rose.copy(alpha = 0.35f),
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

    val pickMultiple = rememberLauncherForActivityResult(PickMultipleVisualMedia(ComposerMaxMediaPick)) { more ->
        mergePicked(more)
    }
    val pickSingle = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) mergePicked(listOf(uri))
    }
    fun launchPick() {
        if (remaining <= 0) return
        val request = PickVisualMediaRequest(PickVisualMedia.ImageAndVideo)
        if (remaining == 1) pickSingle.launch(request) else pickMultiple.launch(request)
    }

    ComposerScaffold(
        title = "存下这些属于我们的时光",
        subtitle = if (uris.isEmpty()) "添加照片或视频" else "已选 ${uris.size} 项 · 点击预览",
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
                    color = SoftSurface,
                    border = BorderStroke(1.dp, SoftOutline),
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(56.dp).background(Blush, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.AddPhotoAlternate, null, tint = Rose, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("添加照片或视频", fontWeight = FontWeight.SemiBold, color = DeepRose)
                        Text("最多 $ComposerMaxMediaPick 项，支持混选", style = MaterialTheme.typography.bodySmall, color = Stone)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uris.chunked(3).forEachIndexed { rowIndex, row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            row.forEachIndexed { colIndex, uri ->
                                val index = rowIndex * 3 + colIndex
                                val isVideo = remember(uri) {
                                    context.contentResolver.getType(uri)?.startsWith("video/") == true
                                }
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Blush)
                                        .clickable { previewIndex = index },
                                ) {
                                    if (isVideo) {
                                        Box(
                                            Modifier.fillMaxSize().background(Peach.copy(alpha = 0.45f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            AsyncImage(
                                                model = uri,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop,
                                            )
                                            Icon(
                                                Icons.Rounded.PlayCircle,
                                                null,
                                                tint = Color.White,
                                                modifier = Modifier.size(28.dp),
                                            )
                                        }
                                    } else {
                                        AsyncImage(
                                            model = uri,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            uris = uris.filterNot { it.toString() == uri.toString() }
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(28.dp),
                                    ) {
                                        Icon(
                                            Icons.Rounded.Cancel,
                                            "移除",
                                            tint = Color.White,
                                            modifier = Modifier
                                                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                                                .padding(2.dp)
                                                .size(16.dp),
                                        )
                                    }
                                    Text(
                                        "${index + 1}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(6.dp)
                                            .background(Rose.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 5.dp, vertical = 1.dp),
                                    )
                                }
                            }
                            repeat(3 - row.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (remaining > 0) {
                    TextButton(
                        onClick = ::launchPick,
                        colors = ButtonDefaults.textButtonColors(contentColor = Rose),
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
            val isVideo = context.contentResolver.getType(uri)?.startsWith("video/") == true
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
fun AnniversaryComposeScreen(
    onClose: () -> Unit,
    onSave: (String, String, AnniversaryType) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().plusMonths(1).toString()) }
    var type by rememberSaveable { mutableStateOf(AnniversaryType.YEARLY) }

    ComposerScaffold(
        title = "新建纪念日",
        subtitle = "把重要的日子好好放进我们的日历",
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
                color = Blush.copy(alpha = 0.65f),
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
                        Icon(Icons.Rounded.Favorite, null, tint = Rose)
                    }
                    Column {
                        Text("纪念类型", fontWeight = FontWeight.SemiBold, color = DeepRose)
                        Text("年度会循环倒数，里程碑指向那天", style = MaterialTheme.typography.bodySmall, color = Stone)
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
    var unlockOnPartnerBind by rememberSaveable { mutableStateOf(!linked) }

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
                Text("未绑定另一半时，只能写时间胶囊", color = Stone, style = MaterialTheme.typography.bodySmall)
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
                        colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = Rose),
                    )
                    Text("在绑定另一半以后，自动开启", color = Stone)
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
