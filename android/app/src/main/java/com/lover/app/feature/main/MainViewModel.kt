package com.lover.app.feature.main

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lover.app.core.data.AppRepository
import com.lover.app.core.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class MainTab { HOME, TIMELINE, ANNIVERSARY, LETTERS, PROFILE }

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: AppRepository,
) : ViewModel() {
    val data = repository.state
    private val _selectedTab = MutableStateFlow(MainTab.HOME)
    val selectedTab = _selectedTab.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _restoreComplete = MutableStateFlow(false)
    val restoreComplete = _restoreComplete.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = repository.state.first { it.sessionLoaded }
            if (loaded.accessToken != null) {
                runCatching { repository.restoreSession() }
                    .onFailure { _message.value = it.message ?: "刷新失败，已显示缓存" }
            }
            _restoreComplete.value = true
        }
    }

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
    }

    fun addMedia(uri: Uri, caption: String, date: String) = launchAction {
        LocalDate.parse(date)
        repository.addMedia(uri, caption, date)
    }

    fun addAnniversary(title: String, date: String, type: AnniversaryType) = launchAction {
        require(title.isNotBlank()) { "请填写纪念日名称" }
        LocalDate.parse(date)
        repository.addAnniversary(title, date, type)
    }

    fun addLetter(title: String, content: String, type: LetterType, unlockDate: String?) = launchAction {
        validateLetter(title, content, type, unlockDate)
        repository.addLetter(title, content, type, unlockDate)
    }

    fun refresh() = viewModelScope.launch {
        runCatching { repository.restoreSession() }
            .onFailure { _message.value = it.message ?: "刷新失败" }
    }
    fun logout() = launchAction { repository.logout() }
    fun requestUnbinding(reason: String?) = launchAction { repository.requestUnbinding(reason) }
    fun confirmUnbinding(id: String) = launchAction { repository.confirmUnbinding(id) }
    fun cancelUnbinding(id: String) = launchAction { repository.cancelUnbinding(id) }
    fun clearMessage() { _message.value = null }

    private fun launchAction(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }
            .onSuccess { _message.value = "已保存" }
            .onFailure { _message.value = it.message ?: "操作失败" }
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
    }
}
