package com.lover.app.feature.couple

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lover.app.core.data.AppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CoupleViewModel @Inject constructor(
    private val repository: AppRepository,
) : ViewModel() {
    private val _inviteCode = MutableStateFlow<String?>(null)
    val inviteCode = _inviteCode.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun prepareInvite() {
        _inviteCode.value = UUID.randomUUID().toString().take(6).uppercase()
    }

    fun enterCreatedSpace(togetherDate: String) = viewModelScope.launch {
        val code = _inviteCode.value ?: return@launch
        runCatching { repository.createInvite(togetherDate, code) }
            .onFailure { _error.value = it.message }
    }

    fun bind(code: String, togetherDate: String) = viewModelScope.launch {
        runCatching { repository.bindInvite(code.trim(), togetherDate) }
            .onFailure { _error.value = it.message }
    }
}
