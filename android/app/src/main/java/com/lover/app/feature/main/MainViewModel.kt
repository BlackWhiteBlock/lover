package com.lover.app.feature.main

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lover.app.core.data.AppRepository
import com.lover.app.core.data.InviteSession
import com.lover.app.core.model.*
import com.lover.app.core.network.BackendException
import com.lover.app.core.notice.NoticeStore
import com.lover.app.core.util.InviteLinks
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class MainTab { HOME, TIMELINE, ANNIVERSARY, LETTERS, PROFILE }

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: AppRepository,
    private val noticeStore: NoticeStore,
    private val inviteSession: InviteSession,
) : ViewModel() {
    val data = repository.state
    val pendingInviteCode = inviteSession.pendingCode
    private val _selectedTab = MutableStateFlow(MainTab.HOME)
    val selectedTab = _selectedTab.asStateFlow()
    private val _restoreComplete = MutableStateFlow(false)
    val restoreComplete = _restoreComplete.asStateFlow()
    private val _lastInvite = MutableStateFlow<InviteResponse?>(null)
    val lastInvite = _lastInvite.asStateFlow()
    private val _openBindSheet = MutableStateFlow(false)
    val openBindSheet = _openBindSheet.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = repository.state.first { it.sessionLoaded }
            if (loaded.accessToken != null) {
                runCatching { repository.restoreSession() }
                    .onFailure { error ->
                        // 刷新失败并已清会话时，交给登录页，不再弹「token 已过期」
                        if (repository.state.value.accessToken != null) {
                            noticeStore.error(error.message ?: "刷新失败，已显示缓存")
                        }
                    }
            }
            _restoreComplete.value = true
        }
        viewModelScope.launch {
            inviteSession.pendingCode.collect { code ->
                if (!code.isNullOrBlank() && data.value.accessToken != null) {
                    openBindFromPendingInvite()
                }
            }
        }
        viewModelScope.launch {
            data.map { it.accessToken }.distinctUntilChanged().collect { token ->
                if (token != null && !inviteSession.pendingCode.value.isNullOrBlank()) {
                    openBindFromPendingInvite()
                }
            }
        }
    }

    private fun openBindFromPendingInvite() {
        _selectedTab.value = MainTab.PROFILE
        _openBindSheet.value = true
    }

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
    }

    fun dismissBindSheet() {
        _openBindSheet.value = false
    }

    fun requestBindSheet() {
        _openBindSheet.value = true
    }

    fun clearPendingInvite() {
        inviteSession.clear()
    }

    fun createInvite(togetherDate: String? = null, onDone: (InviteResponse) -> Unit = {}) = launchAction(null) {
        requireSpaceOrCreating(togetherDate)
        val invite = repository.createInvite(togetherDate)
        _lastInvite.value = invite
        onDone(invite)
        noticeStore.success("邀请已生成")
    }

    fun acceptInvite(code: String) = launchAction("绑定成功") {
        require(code.trim().length >= 6) { "请输入有效邀请码" }
        repository.acceptInvite(code)
        inviteSession.clear()
        _openBindSheet.value = false
        _lastInvite.value = null
    }

    fun resolveInviteUrl(invite: InviteResponse?): String {
        val value = invite ?: _lastInvite.value ?: return ""
        return value.inviteUrl?.takeIf { it.isNotBlank() } ?: InviteLinks.buildUrl(value.code)
    }

    private fun requireSpaceOrCreating(togetherDate: String?) {
        if (repository.state.value.activeSpaceId == null) {
            require(!togetherDate.isNullOrBlank()) { "创建空间时需要设置在一起的日期" }
            LocalDate.parse(togetherDate)
        }
    }

    private fun requireSpace() {
        require(repository.state.value.activeSpaceId != null) {
            "请先在「我们」邀请或绑定另一半，创建你们的空间"
        }
    }

    fun addMedia(uris: List<Uri>, caption: String, date: String) = launchAction("已保存") {
        requireSpace()
        require(uris.isNotEmpty()) { "请选择至少一张照片或视频" }
        LocalDate.parse(date)
        repository.addMediaBatch(uris, caption, date)
    }

    fun updateMedia(id: String, caption: String, date: String) = launchAction("已更新") {
        requireSpace()
        LocalDate.parse(date)
        repository.updateMedia(id, caption, date)
    }

    fun deleteMedia(id: String) = launchAction("已删除") {
        requireSpace()
        repository.deleteMedia(id)
    }

    fun addAnniversary(title: String, date: String, type: AnniversaryType) = launchAction("已保存") {
        requireSpace()
        require(title.isNotBlank()) { "请填写纪念日名称" }
        LocalDate.parse(date)
        repository.addAnniversary(title, date, type)
    }

    fun addLetter(title: String, content: String, type: LetterType, unlockDate: String?) = launchAction("已保存") {
        requireSpace()
        validateLetter(title, content, type, unlockDate)
        repository.addLetter(title, content, type, unlockDate)
    }

    fun refresh() = viewModelScope.launch {
        runCatching { repository.restoreSession() }
            .onFailure { error ->
                if (repository.state.value.accessToken != null) {
                    noticeStore.error(error.message ?: "刷新失败")
                }
            }
    }

    fun logout() = viewModelScope.launch {
        noticeStore.clear()
        runCatching { repository.logout() }
            .onFailure { error ->
                // 本地会话已清理；仅网络失败时提示，不把鉴权失败抛到登录页
                if (!isAuthFailure(error) && repository.state.value.accessToken != null) {
                    noticeStore.error(error.message ?: "退出失败")
                }
            }
    }

    fun requestUnbinding(reason: String?) = launchAction("已提交") {
        repository.requestUnbinding(reason)
    }

    fun confirmUnbinding(id: String) = launchAction(null) {
        repository.confirmUnbinding(id)
    }

    fun cancelUnbinding(id: String) = launchAction("已取消") {
        repository.cancelUnbinding(id)
    }

    private fun launchAction(
        successMessage: String?,
        block: suspend () -> Unit,
    ) = viewModelScope.launch {
        runCatching { block() }
            .onSuccess {
                if (!successMessage.isNullOrBlank()) {
                    noticeStore.success(successMessage)
                }
            }
            .onFailure { error ->
                if (isAuthFailure(error) && repository.state.value.accessToken == null) {
                    // 会话已被刷新链路清掉，避免登录页再弹 token 过期
                    noticeStore.clear()
                } else {
                    noticeStore.error(error.message ?: "操作失败")
                }
            }
    }

    companion object {
        fun validateLetter(
            title: String,
            content: String,
            type: LetterType,
            unlockDate: String?,
            today: LocalDate = LocalDate.now(),
        ) {
            require(title.isNotBlank()) { "请填写标题" }
            require(content.isNotBlank()) { "请写下想说的话" }
            if (type == LetterType.CAPSULE) {
                require(!unlockDate.isNullOrBlank()) { "请选择解锁日期" }
                require(LocalDate.parse(unlockDate).isAfter(today)) { "解锁日期必须晚于今天" }
            }
        }

        private fun isAuthFailure(error: Throwable): Boolean {
            val code = (error as? BackendException)?.code
            if (code == "UNAUTHORIZED" || code == "HTTP_401") return true
            val message = error.message.orEmpty()
            return message.contains("令牌") || message.contains("过期") || message.contains("请先登录") ||
                message.contains("会话已失效")
        }
    }
}
