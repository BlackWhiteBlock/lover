package com.lover.app.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lover.app.core.design.Blush
import com.lover.app.core.design.Peach
import com.lover.app.core.design.Rose

@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    var phone by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    val message by viewModel.message.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Blush, MaterialTheme.colorScheme.background))),
    ) {
        Box(
            Modifier
                .offset(x = 230.dp, y = (-40).dp)
                .size(210.dp)
                .background(Peach.copy(alpha = .45f), CircleShape),
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 440.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = null,
                tint = Rose,
                modifier = Modifier.size(54.dp),
            )
            Text("lover.", style = MaterialTheme.typography.displayLarge)
            Text("TWO HEARTS · ONE WORLD", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(42.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .92f)),
                elevation = CardDefaults.cardElevation(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("欢迎回来", style = MaterialTheme.typography.headlineMedium)
                    Text("用手机号进入两个人的专属世界", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it.filter(Char::isDigit).take(11) },
                        label = { Text("手机号") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it.filter(Char::isDigit).take(6) },
                            label = { Text("验证码") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalButton(
                            onClick = { viewModel.sendCode(phone) },
                            modifier = Modifier.padding(top = 8.dp),
                        ) { Text("获取") }
                    }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                    Button(
                        onClick = { viewModel.login(phone, code) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = phone.isNotBlank() && code.isNotBlank(),
                    ) { Text("登录 / 注册") }
                    Text(
                        "开发环境可直接输入验证码（例如 123456）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
