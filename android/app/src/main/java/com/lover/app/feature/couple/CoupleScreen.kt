package com.lover.app.feature.couple

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lover.app.core.design.Blush
import com.lover.app.core.design.Rose
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
            .background(MaterialTheme.colorScheme.background)
            .widthIn(max = 448.dp)
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(84.dp).background(Blush, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Favorite, null, tint = Rose, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("建立我们的小宇宙", style = MaterialTheme.typography.headlineMedium)
        Text("只属于两个人的私密空间", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(
                selected = mode == "create",
                onClick = { mode = "create" },
                label = { Text("创建邀请") },
                leadingIcon = { Icon(Icons.Rounded.Favorite, null, Modifier.size(18.dp)) },
            )
            FilterChip(
                selected = mode == "bind",
                onClick = { mode = "bind" },
                label = { Text("绑定邀请码") },
                leadingIcon = { Icon(Icons.Rounded.Link, null, Modifier.size(18.dp)) },
            )
        }
        Spacer(Modifier.height(24.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("我们在一起的日期", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it.take(10) },
                    label = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (mode == "create") {
                    if (generatedCode == null) {
                        Button(
                            onClick = viewModel::prepareInvite,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("生成邀请码") }
                    } else {
                        Text("把邀请码发给 TA", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(color = Blush, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                generatedCode.orEmpty(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = Rose,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                        Button(
                            onClick = { viewModel.enterCreatedSpace(date) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("进入情侣空间") }
                    }
                } else {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.uppercase().take(8) },
                        label = { Text("邀请码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { viewModel.bind(code, date) },
                        enabled = code.length >= 4,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("确认绑定") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
