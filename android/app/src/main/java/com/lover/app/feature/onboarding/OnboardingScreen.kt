package com.lover.app.feature.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lover.app.R
import com.lover.app.core.design.Blush
import com.lover.app.core.design.LoverDateField
import com.lover.app.core.design.Rose
import com.lover.app.core.design.SoftOutline
import com.lover.app.core.design.SoftTextField
import com.lover.app.core.design.Stone
import com.lover.app.core.design.WarmBackground
import java.time.LocalDate

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel) {
    var nickname by rememberSaveable { mutableStateOf("") }
    var gender by rememberSaveable { mutableStateOf("unspecified") }
    var birthday by rememberSaveable { mutableStateOf(LocalDate.now().minusYears(20).toString()) }
    var spaceName by rememberSaveable { mutableStateOf("我们的小宇宙") }
    var avatarUri by rememberSaveable { mutableStateOf<String?>(null) }
    val submitting by viewModel.submitting.collectAsState()

    val pickAvatar = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        avatarUri = uri?.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Blush.copy(alpha = 0.55f), WarmBackground)))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 56.dp, bottom = 40.dp)
            .widthIn(max = 448.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("创建我们的空间", style = MaterialTheme.typography.headlineMedium)
        Text("先完善你的资料，再绑定另一半走进来", color = Stone)
        Spacer(Modifier.height(28.dp))

        OnboardingAvatarPicker(
            avatarUri = avatarUri,
            onClick = {
                pickAvatar.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
        )
        Spacer(Modifier.height(24.dp))

        SoftTextField(
            value = nickname,
            onValueChange = { nickname = it.take(30) },
            label = "昵称",
            placeholder = "怎么称呼你",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text("性别", color = Stone, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            GenderChip("男", "male", gender) { gender = it }
            GenderChip("女", "female", gender) { gender = it }
            GenderChip("不愿透露", "unspecified", gender) { gender = it }
        }
        Spacer(Modifier.height(12.dp))
        LoverDateField(
            value = birthday,
            onValueChange = { birthday = it },
            label = "生日",
            maxDate = LocalDate.now(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        SoftTextField(
            value = spaceName,
            onValueChange = { spaceName = it.take(40) },
            label = "空间昵称",
            placeholder = "我们的小宇宙",
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                // 头像可选：选了则创建空间后上传并写入用户资料
                viewModel.submit(
                    nickname = nickname,
                    gender = gender,
                    birthday = birthday,
                    spaceName = spaceName.ifBlank { "我们的小宇宙" },
                    avatarUri = avatarUri?.let(Uri::parse),
                )
            },
            enabled = nickname.isNotBlank() && !submitting,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Rose),
        ) {
            Text(if (submitting) "创建中…" else "完成并进入")
        }
    }
}

@Composable
private fun OnboardingAvatarPicker(
    avatarUri: String?,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Blush)
                .border(2.dp, SoftOutline.copy(alpha = 0.8f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (!avatarUri.isNullOrBlank()) {
                AsyncImage(
                    model = Uri.parse(avatarUri),
                    contentDescription = "头像预览",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.lover_logo),
                    contentDescription = "默认头像",
                    modifier = Modifier.size(52.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(30.dp)
                .background(Rose, CircleShape)
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.PhotoCamera,
                contentDescription = "设置头像",
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun GenderChip(
    label: String,
    value: String,
    selected: String,
    onSelect: (String) -> Unit,
) {
    FilterChip(
        selected = selected == value,
        onClick = { onSelect(value) },
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
            selected = selected == value,
            borderColor = SoftOutline,
            selectedBorderColor = Rose.copy(alpha = 0.35f),
        ),
    )
}
