package com.lover.app.feature.main

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lover.app.core.data.AppRepository
import com.lover.app.core.model.*
import com.lover.app.core.network.isUnauthorized
import com.lover.app.core.notice.NoticeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class MainTab { HOME, TIMELINE, ANNIVERSARY, LETTERS, PROFILE }

/** 时光页展示中的本地上传任务（未入库前） */
data class PendingMediaUpload(
    val id: String,
    val caption: String,
    val mediaDate: String,
    val previewUri: Uri,
    val assetCount: Int,
    val completed: Int = 0,
    val total: Int,
) {
    val progress: Float
        get() = if (total <= 0) 0f else (completed.toFloat() / total).coerceIn(0f, 1f)
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: AppRepository,
    private val noticeStore: NoticeStore,
) : ViewModel() {
    val data = repository.state
    private val _selectedTab = MutableStateFlow(MainTab.HOME)
    val selectedTab = _selectedTab.asStateFlow()
    private val _restoreComplete = MutableStateFlow(false)
    val restoreComplete = _restoreComplete.asStateFlow()
    private val _promptTogetherDate = MutableStateFlow(false)
    val promptTogetherDate = _promptTogetherDate.asStateFlow()
    private val _pendingMediaUploads = MutableStateFlow<List<PendingMediaUpload>>(emptyList())
    val pendingMediaUploads = _pendingMediaUploads.asStateFlow()

    init {
        // 登出后 Tab 立刻回到 HOME，避免再次登录时先闪「我们」再切「空间」
        viewModelScope.launch {
            repository.state.collect { state ->
                if (state.accessToken == null) {
                    _selectedTab.value = MainTab.HOME
                }
            }
        }
        viewModelScope.launch {
            val loaded = repository.state.first { it.sessionLoaded }
            if (loaded.accessToken != null) {
                runCatching { repository.restoreSession() }
                    .onFailure { error ->
                        if (error.isUnauthorized() || repository.state.value.accessToken == null) {
                            noticeStore.clear()
                        } else {
                            noticeStore.error(error.message ?: "刷新失败，已显示缓存")
                        }
                    }
            }
            _restoreComplete.value = true
        }
    }

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
    }

    fun openMediaDetail(item: MediaItem, onReady: (MediaItem) -> Unit) = viewModelScope.launch {
        runCatching { repository.ensureMediaOriginals(item) }
            .onSuccess(onReady)
            .onFailure { error ->
                if (error.isUnauthorized() || repository.state.value.accessToken == null) {
                    noticeStore.clear()
                } else {
                    // 原图签发失败时仍打开详情（可用缩略图预览）
                    noticeStore.error(error.message ?: "原图加载失败")
                    onReady(item)
                }
            }
    }

    fun requestBind(phone: String) = launchAction("已发送绑定请求") {
        require(phone.trim().length == 11) { "请输入对方手机号" }
        repository.requestBind(phone)
    }

    fun acceptBind(id: String) = launchAction("绑定成功") {
        val result = repository.acceptBind(id)
        if (result.needsTogetherDate) {
            _promptTogetherDate.value = true
        }
    }

    fun rejectBind(id: String) = launchAction("已拒绝") {
        repository.rejectBind(id)
    }

    fun cancelBind(id: String) = launchAction("已取消") {
        repository.cancelBind(id)
    }

    fun dismissTogetherDatePrompt() {
        _promptTogetherDate.value = false
    }

    fun saveTogetherDate(date: String?) = launchAction(if (date == null) null else "已保存") {
        if (date != null) LocalDate.parse(date)
        repository.updateTogetherDate(date)
        _promptTogetherDate.value = false
    }

    fun updateCoupleCard(
        name: String,
        togetherDate: String?,
        coverUri: Uri?,
        clearCover: Boolean,
    ) = launchAction("已保存") {
        require(name.isNotBlank()) { "请填写空间名称" }
        if (togetherDate != null) LocalDate.parse(togetherDate)
        repository.updateCoupleCard(
            name = name.trim(),
            togetherDate = togetherDate,
            coverUri = coverUri,
            clearCover = clearCover,
        )
    }

    private fun requireSpace() {
        require(repository.state.value.profileCompleted && repository.state.value.personalSpaceId != null) {
            "请先完成空间创建"
        }
    }

    fun addMedia(uris: List<Uri>, caption: String, date: String) {
        if (!repository.state.value.profileCompleted || repository.state.value.personalSpaceId == null) {
            noticeStore.error("请先完成空间创建")
            return
        }
        if (uris.isEmpty()) {
            noticeStore.error("请选择至少一张照片或视频")
            return
        }
        runCatching { LocalDate.parse(date) }.onFailure {
            noticeStore.error("日期无效")
            return
        }
        val jobId = java.util.UUID.randomUUID().toString()
        val pending = PendingMediaUpload(
            id = jobId,
            caption = caption.trim(),
            mediaDate = date,
            previewUri = uris.first(),
            assetCount = uris.size,
            completed = 0,
            total = uris.size,
        )
        _pendingMediaUploads.value = listOf(pending) + _pendingMediaUploads.value
        _selectedTab.value = MainTab.TIMELINE
        viewModelScope.launch {
            runCatching {
                repository.addMediaBatch(uris, caption, date) { completed, total ->
                    _pendingMediaUploads.value = _pendingMediaUploads.value.map { item ->
                        if (item.id == jobId) item.copy(completed = completed, total = total) else item
                    }
                }
            }.onSuccess {
                _pendingMediaUploads.value = _pendingMediaUploads.value.filterNot { it.id == jobId }
                noticeStore.success("已保存")
            }.onFailure { error ->
                _pendingMediaUploads.value = _pendingMediaUploads.value.filterNot { it.id == jobId }
                if (error.isUnauthorized() || repository.state.value.accessToken == null) {
                    noticeStore.clear()
                } else {
                    noticeStore.error(error.message ?: "上传失败")
                }
            }
        }
    }

    fun updateMedia(
        id: String,
        caption: String,
        date: String? = null,
        newUris: List<Uri> = emptyList(),
        removedPartIds: List<String> = emptyList(),
    ) = launchAction("已保存") {
        requireSpace()
        date?.let { LocalDate.parse(it) }
        repository.updateMedia(id, caption, date, newUris, removedPartIds)
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

    fun addLetter(
        title: String,
        content: String,
        type: LetterType,
        unlockDate: String?,
        unlockOnPartnerBind: Boolean = false,
    ) = launchAction("已保存") {
        requireSpace()
        validateLetter(
            title,
            content,
            type,
            unlockDate,
            unlockOnPartnerBind,
            linked = repository.state.value.linked,
        )
        repository.addLetter(title, content, type, unlockDate, unlockOnPartnerBind)
    }

    fun refresh() = viewModelScope.launch {
        runCatching { repository.restoreSession() }
            .onFailure { error ->
                if (error.isUnauthorized() || repository.state.value.accessToken == null) {
                    noticeStore.clear()
                } else {
                    noticeStore.error(error.message ?: "刷新失败")
                }
            }
    }

    fun logout() = viewModelScope.launch {
        noticeStore.clear()
        runCatching { repository.logout() }
            .onFailure { error ->
                if (!error.isUnauthorized() && repository.state.value.accessToken != null) {
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
                if (error.isUnauthorized() || repository.state.value.accessToken == null) {
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
            unlockOnPartnerBind: Boolean = false,
            linked: Boolean = true,
            today: LocalDate = LocalDate.now(),
        ) {
            require(title.isNotBlank()) { "请填写标题" }
            require(content.isNotBlank()) { "请写下想说的话" }
            if (type == LetterType.INSTANT) {
                require(linked) { "绑定另一半后才能发送即时信" }
            }
            if (type == LetterType.CAPSULE && !unlockOnPartnerBind) {
                require(!unlockDate.isNullOrBlank()) { "请选择解锁日期，或勾选绑定后开启" }
                require(LocalDate.parse(unlockDate).isAfter(today)) { "解锁日期必须晚于今天" }
            }
        }
    }
}
