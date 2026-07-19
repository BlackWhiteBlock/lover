package com.lover.app.feature.auth

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lover.app.core.design.Blush
import com.lover.app.core.design.LoverWordmark
import com.lover.app.core.design.Peach
import com.lover.app.core.design.Rose
import com.lover.app.core.design.SoftTextField
import com.lover.app.core.design.Stone

@Composable
fun AuthScreen(viewModel: AuthViewModel) {
    var phone by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    val message by viewModel.message.collectAsState()
    val cooldown by viewModel.cooldownSeconds.collectAsState()
    val pnvsReady by viewModel.pnvsReady.collectAsState()
    val showSms by viewModel.showSmsFallback.collectAsState()
    val pnvsBusy by viewModel.pnvsBusy.collectAsState()
    val pnvsHint by viewModel.pnvsHint.collectAsState()
    val activity = LocalContext.current as? Activity

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Blush, MaterialTheme.colorScheme.background))),
    ) {
        Box(
            modifier = Modifier
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
            LoverWordmark(logoSize = 72.dp, usePhoto = true)
            Spacer(modifier = Modifier.height(36.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .94f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(32.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(26.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("欢迎回来", style = MaterialTheme.typography.headlineMedium)
                    Text("用手机号进入两个人的专属世界", color = Stone)

                    Button(
                        onClick = {
                            if (activity != null) viewModel.oneClickLogin(activity)
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = pnvsReady && !pnvsBusy && activity != null,
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Rose),
                    ) {
                        Text(
                            when {
                                pnvsBusy -> "正在拉起…"
                                pnvsReady -> "本机号码一键登录"
                                else -> "本机号码一键登录（暂不可用）"
                            },
                        )
                    }
                    pnvsHint?.let {
                        Text(
                            it,
                            color = Stone,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (!showSms) {
                        TextButton(
                            onClick = { viewModel.showSmsFallback() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("其他手机号 / 验证码登录", color = Stone)
                        }
                    }

                    if (showSms) {
                        SoftTextField(
                            value = phone,
                            onValueChange = { phone = it.filter(Char::isDigit).take(11) },
                            label = "手机号",
                            placeholder = "请输入手机号",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SoftTextField(
                                value = code,
                                onValueChange = { code = it.filter(Char::isDigit).take(6) },
                                label = "验证码",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            FilledTonalButton(
                                onClick = { viewModel.sendCode(phone) },
                                enabled = phone.length == 11 && cooldown == 0,
                                modifier = Modifier.padding(top = 8.dp).height(56.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Blush,
                                    contentColor = Rose,
                                ),
                            ) {
                                Text(if (cooldown > 0) "${cooldown}s" else "获取")
                            }
                        }
                        Button(
                            onClick = { viewModel.login(phone, code) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            enabled = phone.length == 11 && code.length == 6,
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Rose),
                        ) { Text("登录 / 注册") }
                    }

                    message?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.clickable { },
                        )
                    }
                }
            }
        }
    }
}
