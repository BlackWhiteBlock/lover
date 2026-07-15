package com.lover.app.feature.couple

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lover.app.core.design.Blush
import com.lover.app.core.design.DeepRose
import com.lover.app.core.design.LoverDateField
import com.lover.app.core.design.Rose
import com.lover.app.core.design.SoftOutline
import com.lover.app.core.design.SoftTextField
import com.lover.app.core.design.Stone
import com.lover.app.core.design.WarmBackground
import java.time.LocalDate

@Composable
fun CoupleScreen(viewModel: CoupleViewModel) {
    var mode by rememberSaveable { mutableStateOf("create") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().minusYears(1).toString()) }
    var code by rememberSaveable { mutableStateOf("") }
    val generatedCode by viewModel.inviteCode.collectAsState()
    val error by viewModel.error.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Blush.copy(alpha = 0.55f), WarmBackground)))
            .widthIn(max = 448.dp)
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(84.dp).background(Blush, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Favorite, null, tint = Rose, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("建立我们的小宇宙", style = MaterialTheme.typography.headlineMedium)
        Text("只属于两个人的私密空间", color = Stone)
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SoftModeChip(
                selected = mode == "create",
                onClick = { mode = "create" },
                label = "创建邀请",
                icon = Icons.Rounded.Favorite,
            )
            SoftModeChip(
                selected = mode == "bind",
                onClick = { mode = "bind" },
                label = "绑定邀请码",
                icon = Icons.Rounded.Link,
            )
        }
        Spacer(Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(32.dp),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (mode == "create") {
                    Text("我们在一起的日期", fontWeight = FontWeight.SemiBold, color = DeepRose)
                    LoverDateField(
                        value = date,
                        onValueChange = { date = it },
                        label = "在一起的那天",
                        maxDate = LocalDate.now(),
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = "从这一天开始计算相爱天数",
                    )
                    if (generatedCode == null) {
                        Button(
                            onClick = { viewModel.createInvite(date) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Rose),
                        ) { Text("创建空间并生成邀请码") }
                    } else {
                        Text("把邀请码发给 TA", color = Stone)
                        Surface(
                            color = Blush,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                generatedCode.orEmpty(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = Rose,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                        Text("空间已创建，正在进入…", color = Stone)
                    }
                } else {
                    Text("接受后将使用邀请空间设置的在一起日期", color = Stone)
                    SoftTextField(
                        value = code,
                        onValueChange = { code = it.uppercase().take(8) },
                        label = "邀请码",
                        placeholder = "输入对方分享的邀请码",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { viewModel.bind(code) },
                        enabled = code.length >= 6,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Rose),
                    ) { Text("确认绑定") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun SoftModeChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: ImageVector,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
        shape = RoundedCornerShape(20.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Blush,
            selectedLabelColor = Rose,
            selectedLeadingIconColor = Rose,
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
