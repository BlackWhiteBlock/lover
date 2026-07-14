package com.lover.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lover.app.core.data.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AppRepository,
) : ViewModel() {
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun sendCode(phone: String) {
        viewModelScope.launch {
            runCatching { repository.sendSms(phone) }
                .onSuccess { response ->
                    _message.value = response.devCode?.let { "开发验证码：$it" }
                        ?: "验证码已发送，${response.cooldownSeconds} 秒后可重试"
                }
                .onFailure { _message.value = it.message }
        }
    }

    fun login(phone: String, code: String) = viewModelScope.launch {
        runCatching { repository.login(phone.trim(), code.trim()) }
            .onFailure { _message.value = it.message }
    }
}
