package com.lover.app.feature.couple

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lover.app.core.data.AppRepository
import com.lover.app.core.model.InviteResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CoupleViewModel @Inject constructor(
    private val repository: AppRepository,
) : ViewModel() {
    private val _invite = MutableStateFlow<InviteResponse?>(null)
    val invite = _invite.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun createInvite(togetherDate: String? = null) = viewModelScope.launch {
        runCatching { repository.createInvite(togetherDate) }
            .onSuccess { _invite.value = it }
            .onFailure { _error.value = it.message }
    }

    fun bind(code: String) = viewModelScope.launch {
        runCatching { repository.acceptInvite(code.trim()) }
            .onFailure { _error.value = it.message }
    }
}
