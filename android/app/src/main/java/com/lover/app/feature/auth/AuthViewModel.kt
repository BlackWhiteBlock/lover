package com.lover.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lover.app.core.data.AppRepository
import com.lover.app.core.notice.NoticeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AppRepository,
    private val noticeStore: NoticeStore,
) : ViewModel() {
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _cooldownSeconds = MutableStateFlow(0)
    val cooldownSeconds = _cooldownSeconds.asStateFlow()

    private var countdownJob: Job? = null

    fun sendCode(phone: String) {
        if (_cooldownSeconds.value > 0) return
        viewModelScope.launch {
            _message.value = null
            runCatching { repository.sendSms(phone.trim()) }
                .onSuccess { response ->
                    noticeStore.info("验证码已发送")
                    val seconds = response.cooldownSeconds.takeIf { it > 0 } ?: 60
                    startCooldown(seconds)
                }
                .onFailure { _message.value = it.message }
        }
    }

    private fun startCooldown(seconds: Int) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (left in seconds downTo 1) {
                _cooldownSeconds.value = left
                delay(1_000)
            }
            _cooldownSeconds.value = 0
        }
    }

    fun login(phone: String, code: String) = viewModelScope.launch {
        _message.value = null
        noticeStore.clear()
        runCatching { repository.login(phone.trim(), code.trim()) }
            .onFailure { _message.value = it.message }
        // 登录成功不弹「已保存」等提示，直接进入下一页
    }
}
