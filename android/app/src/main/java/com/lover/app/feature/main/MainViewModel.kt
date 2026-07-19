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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
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
    val fileFraction: Float = 0f,
    val phase: String = "准备上传",
) {
    val progress: Float
        get() {
            if (total <= 0) return 0f
            val overall = (completed + fileFraction.coerceIn(0f, 1f)) / total
            return overall.coerceIn(0f, 1f)
        }
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
    private val _coupleRevealEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val coupleRevealEvent = _coupleRevealEvent.asSharedFlow()

    private var bindPollJob: Job? = null

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
                            noticeStore.clearAll()
                        } else {
                            noticeStore.error(error.message ?: "刷新失败，已显示缓存")
                        }
                    }
                refreshMediaUnreadBadge()
            }
            _restoreComplete.value = true
        }
        // linked: false → true 时触发恋爱主题转场；冷启动已绑定则静默记为已播过
        viewModelScope.launch {
            var previousLinked: Boolean? = null
            repository.state.collect { state ->
                if (state.accessToken == null) {
                    previousLinked = null
                    return@collect
                }
                val linkId = state.coupleLinkId
                    ?: state.couple?.coupleLinkId?.takeIf { it.isNotBlank() }
                val prev = previousLinked
                previousLinked = state.linked
                when {
                    prev == null && state.linked && !linkId.isNullOrBlank() &&
                        state.coupleThemeRevealShownLinkId != linkId -> {
                        repository.markCoupleThemeRevealShown(linkId)
                    }
                    prev == false && state.linked && !linkId.isNullOrBlank() &&
                        state.coupleThemeRevealShownLinkId != linkId -> {
                        _coupleRevealEvent.tryEmit(linkId)
                    }
                }
            }
        }
        // 发起人：有未完成的出站绑定时轮询，以便对方确认后立刻进入恋爱转场
        viewModelScope.launch {
            repository.state
                .map { it.pendingOutgoingBind?.id to it.linked }
                .distinctUntilChanged()
                .collect { (outgoingId, linked) ->
                    bindPollJob?.cancel()
                    if (outgoingId != null && !linked) {
                        bindPollJob = viewModelScope.launch {
                            while (isActive) {
                                delay(4_000)
                                runCatching { repository.refreshAll() }
                            }
                        }
                    }
                }
        }
    }

    fun refreshSessionQuietly() = viewModelScope.launch {
        if (repository.state.value.accessToken == null) return@launch
        runCatching { repository.refreshAll() }
        refreshMediaUnreadBadge()
    }

    val mediaUnreadCount = repository.mediaUnreadCount
    val mediaHasMore = repository.mediaHasMore
    val mediaYears = repository.mediaYears
    val mediaYearFilter = repository.mediaYearFilterState

    private suspend fun refreshMediaUnreadBadge() {
        runCatching { repository.refreshMediaUnreadSummary() }
    }

    fun setMediaYearFilter(year: Int?) = viewModelScope.launch {
        runCatching { repository.setMediaYearFilter(year) }
            .onFailure { error ->
                if (!error.isUnauthorized()) {
                    noticeStore.error(error.message ?: "筛选失败")
                }
            }
    }

    fun refreshMediaYears() = viewModelScope.launch {
        runCatching { repository.refreshMediaYears() }
    }

    suspend fun loadUnreadMediaPage(cursor: String?): MediaUnreadPage? =
        runCatching { repository.fetchUnreadMedia(cursor) }.getOrNull()

    fun markAllUnreadMediaRead() = viewModelScope.launch {
        runCatching { repository.markAllUnreadMediaRead() }
    }

    fun markCoupleThemeRevealShown(linkId: String) = viewModelScope.launch {
        repository.markCoupleThemeRevealShown(linkId)
    }

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
        if (tab == MainTab.TIMELINE) {
            refreshMediaYears()
            viewModelScope.launch { refreshMediaUnreadBadge() }
        }
    }

    fun openMediaDetail(item: MediaItem, onReady: (MediaItem) -> Unit) = viewModelScope.launch {
        // 打开即视为已读（对方发布/修改的时光）
        runCatching { repository.markMediaRead(item.id) }
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

    fun updatePersonalCard(spaceName: String, avatarUri: Uri?) = launchAction("已保存") {
        require(spaceName.isNotBlank()) { "请填写空间名称" }
        repository.updatePersonalCard(spaceName.trim(), avatarUri)
    }

    suspend fun lookupUser(phone: String): UserLookupResponse =
        repository.lookupUser(phone)

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
            phase = "准备上传",
        )
        _pendingMediaUploads.value = listOf(pending) + _pendingMediaUploads.value
        _selectedTab.value = MainTab.TIMELINE
        viewModelScope.launch {
            runCatching {
                repository.addMediaBatch(uris, caption, date) { progress ->
                    _pendingMediaUploads.value = _pendingMediaUploads.value.map { item ->
                        if (item.id != jobId) item else item.copy(
                            completed = progress.completedFiles,
                            total = progress.totalFiles,
                            fileFraction = progress.fileFraction,
                            phase = progress.phase,
                        )
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
        assetOrder: List<AppRepository.MediaEditOrderItem>? = null,
    ) = launchAction("已保存") {
        requireSpace()
        date?.let { LocalDate.parse(it) }
        repository.updateMedia(id, caption, date, assetOrder)
    }

    fun deleteMedia(id: String) = launchAction("已删除") {
        requireSpace()
        repository.deleteMedia(id)
    }

    fun loadMoreMedia() = viewModelScope.launch {
        runCatching { repository.loadMoreMedia() }
    }

    fun addAnniversary(title: String, date: String, type: AnniversaryType) = launchAction("已保存") {
        requireSpace()
        require(title.isNotBlank()) { "请填写纪念日名称" }
        LocalDate.parse(date)
        repository.addAnniversary(title, date, type)
    }

    fun updateAnniversary(id: String, title: String, date: String, type: AnniversaryType) = launchAction("已保存") {
        requireSpace()
        require(title.isNotBlank()) { "请填写纪念日名称" }
        LocalDate.parse(date)
        repository.updateAnniversary(id, title, date, type)
    }

    fun deleteAnniversary(id: String) = launchAction("已删除") {
        requireSpace()
        repository.deleteAnniversary(id)
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

    fun deleteLetter(id: String) = launchAction("已删除") {
        requireSpace()
        repository.deleteLetter(id)
    }

    suspend fun signAssetUrl(assetId: String): String =
        repository.signAssetOriginalUrl(assetId)

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
