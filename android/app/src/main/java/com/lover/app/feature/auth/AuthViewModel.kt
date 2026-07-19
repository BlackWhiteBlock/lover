package com.lover.app.feature.auth

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lover.app.core.auth.PnvsLoginHelper
import com.lover.app.core.data.AppRepository
import com.lover.app.core.notice.NoticeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AppRepository,
    private val noticeStore: NoticeStore,
    private val pnvsLoginHelper: PnvsLoginHelper,
) : ViewModel() {
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _cooldownSeconds = MutableStateFlow(0)
    val cooldownSeconds = _cooldownSeconds.asStateFlow()

    private val _pnvsReady = MutableStateFlow(false)
    val pnvsReady = _pnvsReady.asStateFlow()

    private val _showSmsFallback = MutableStateFlow(false)
    val showSmsFallback = _showSmsFallback.asStateFlow()

    private val _pnvsBusy = MutableStateFlow(false)
    val pnvsBusy = _pnvsBusy.asStateFlow()

    private val _pnvsHint = MutableStateFlow<String?>(null)
    val pnvsHint = _pnvsHint.asStateFlow()

    private var countdownJob: Job? = null

    init {
        preparePnvs()
    }

    fun preparePnvs() {
        viewModelScope.launch {
            if (!pnvsLoginHelper.isSdkAvailable()) {
                markPnvsUnavailable("本机号登录需先放入阿里云号码认证 SDK（见 app/libs/README）")
                return@launch
            }
            runCatching { repository.fetchPnvsSdkInfo() }
                .onSuccess { info ->
                    if (!info.enabled || info.secretInfo.isBlank()) {
                        markPnvsUnavailable("服务端未开启本机号登录，请检查 PNVS 配置")
                        return@onSuccess
                    }
                    suspendCancellableCoroutine { cont ->
                        pnvsLoginHelper.prepare(
                            secretInfo = info.secretInfo,
                            privacyUrl = info.privacyUrl,
                            termsUrl = info.termsUrl,
                            onLog = { Log.i(TAG, it) },
                        ) { ready ->
                            if (cont.isActive) cont.resume(ready)
                        }
                    }.also { ready ->
                        _pnvsReady.value = ready
                        if (ready) {
                            _pnvsHint.value = null
                        } else {
                            markPnvsUnavailable("本机号登录暂不可用，请使用验证码")
                        }
                    }
                }
                .onFailure {
                    markPnvsUnavailable("无法获取本机号登录配置，请检查网络与后端")
                }
        }
    }

    private fun markPnvsUnavailable(hint: String) {
        _pnvsReady.value = false
        _pnvsHint.value = hint
        _showSmsFallback.value = true
    }

    fun showSmsFallback() {
        _showSmsFallback.value = true
    }

    fun oneClickLogin(activity: Activity) {
        if (_pnvsBusy.value) return
        viewModelScope.launch {
            _message.value = null
            _pnvsBusy.value = true
            noticeStore.clear()
            val tokenResult = suspendCancellableCoroutine { cont ->
                pnvsLoginHelper.getLoginToken(
                    activity = activity,
                    onLog = { Log.i(TAG, it) },
                ) { result ->
                    if (cont.isActive) cont.resume(result)
                }
            }
            tokenResult
                .onSuccess { token ->
                    runCatching { repository.loginWithPnvsToken(token) }
                        .onFailure {
                            _message.value = it.message
                            _showSmsFallback.value = true
                        }
                }
                .onFailure {
                    val msg = it.message.orEmpty()
                    if (!msg.contains("取消")) {
                        _message.value = msg.ifBlank { "本机号登录失败，请使用短信验证码" }
                    }
                    _showSmsFallback.value = true
                }
            _pnvsBusy.value = false
        }
    }

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
    }

    companion object {
        private const val TAG = "AuthPnvs"
    }
}
