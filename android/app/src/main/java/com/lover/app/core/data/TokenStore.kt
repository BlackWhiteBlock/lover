package com.lover.app.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lover.app.core.model.AppState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.sessionDataStore by preferencesDataStore("lover_session")

@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    private val stateKey = stringPreferencesKey("app_state_v2")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    @Volatile private var currentAccessToken: String? = null
    @Volatile private var currentRefreshToken: String? = null
    @Volatile private var tokensWrittenInProcess = false

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        scope.launch {
            val disk = context.sessionDataStore.data
                .catch { emit(emptyPreferences()) }
                .first()
            val loaded = disk[stateKey]
                ?.let { runCatching { json.decodeFromString<AppState>(it) }.getOrNull() }
                ?.withoutEphemeralSignedUrls()
                ?: AppState()
            val ready = loaded.copy(sessionLoaded = true)
            if (!tokensWrittenInProcess) {
                currentAccessToken = ready.accessToken
                currentRefreshToken = ready.refreshToken
            }
            // 仅冷启动灌入一次；之后 UI 以内存态为准，避免落盘去签名后把缩略图冲掉
            if (!_state.value.sessionLoaded) {
                _state.value = ready
            }
        }
    }

    val snapshot: AppState
        get() {
            val memory = _state.value
            return memory.copy(
                accessToken = if (tokensWrittenInProcess) {
                    currentAccessToken
                } else {
                    currentAccessToken ?: memory.accessToken
                },
                refreshToken = if (tokensWrittenInProcess) {
                    currentRefreshToken
                } else {
                    currentRefreshToken ?: memory.refreshToken
                },
                sessionLoaded = true,
            )
        }

    suspend fun update(transform: (AppState) -> AppState) {
        mutex.withLock {
            val current = _state.value.let { if (it.sessionLoaded) it else snapshot }
            val next = transform(current).copy(loading = false, sessionLoaded = true)
            _state.value = next
            if (!tokensWrittenInProcess) {
                currentAccessToken = next.accessToken
                currentRefreshToken = next.refreshToken
            }
            context.sessionDataStore.edit { preferences ->
                // 落盘去掉签名 URL，避免冷启动复用旧域名；内存 / UI 仍保留本次签名
                preferences[stateKey] = json.encodeToString(next.withoutEphemeralSignedUrls())
            }
        }
    }

    private fun AppState.withoutEphemeralSignedUrls(): AppState = copy(
        media = media.map { item ->
            item.copy(
                assets = item.assets.map { part ->
                    part.copy(url = "", thumbnailUrl = null)
                },
            )
        },
    )

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        tokensWrittenInProcess = true
        currentAccessToken = accessToken
        currentRefreshToken = refreshToken
        // 登录过程中先挡住主界面，等 refreshAll 完成再 sessionReady=true
        update {
            it.copy(
                accessToken = accessToken,
                refreshToken = refreshToken,
                sessionReady = false,
            )
        }
    }

    suspend fun clearSession() {
        mutex.withLock {
            tokensWrittenInProcess = true
            currentAccessToken = null
            currentRefreshToken = null
            _state.value = AppState(sessionLoaded = true)
            context.sessionDataStore.edit { preferences ->
                preferences[stateKey] = json.encodeToString(AppState())
            }
        }
    }
}
