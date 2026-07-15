package com.lover.app.feature.onboarding

import android.net.Uri
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
class OnboardingViewModel @Inject constructor(
    private val repository: AppRepository,
    private val noticeStore: NoticeStore,
) : ViewModel() {
    private val _submitting = MutableStateFlow(false)
    val submitting = _submitting.asStateFlow()

    fun submit(
        nickname: String,
        gender: String,
        birthday: String,
        spaceName: String,
        avatarUri: Uri? = null,
    ) = viewModelScope.launch {
        _submitting.value = true
        runCatching {
            repository.completeOnboarding(
                nickname = nickname,
                gender = gender,
                birthday = birthday,
                spaceName = spaceName,
                avatarUri = avatarUri,
            )
        }.onFailure { noticeStore.error(it.message ?: "创建失败") }
        _submitting.value = false
    }
}
