package com.lover.app.core.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds invite codes arriving from deep links until the bind UI can consume them.
 */
@Singleton
class InviteSession @Inject constructor() {
    private val _pendingCode = MutableStateFlow<String?>(null)
    val pendingCode = _pendingCode.asStateFlow()

    fun offer(code: String) {
        val normalized = code.trim().uppercase()
        if (normalized.length >= 6) {
            _pendingCode.value = normalized
        }
    }

    fun clear() {
        _pendingCode.value = null
    }
}
