package com.lover.app.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
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
    val submitting by viewModel.submitting.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Blush.copy(alpha = 0.55f), WarmBackground)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 40.dp)
            .widthIn(max = 448.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("创建我们的空间", style = MaterialTheme.typography.headlineMedium)
        Text("先完善你的资料，再邀请另一半走进来", color = Stone)
        Spacer(Modifier.height(28.dp))
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
        Text("头像可稍后在设置中上传", color = Stone, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                viewModel.submit(
                    nickname = nickname,
                    gender = gender,
                    birthday = birthday,
                    spaceName = spaceName.ifBlank { "我们的小宇宙" },
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
            containerColor = androidx.compose.ui.graphics.Color.White,
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
