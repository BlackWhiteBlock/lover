package com.lover.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lover.app.core.data.AppRepository
import com.lover.app.core.notice.NoticeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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

    fun sendCode(phone: String) {
        viewModelScope.launch {
            _message.value = null
            runCatching { repository.sendSms(phone) }
                .onSuccess {
                    noticeStore.info("验证码已发送")
                }
                .onFailure { _message.value = it.message }
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
