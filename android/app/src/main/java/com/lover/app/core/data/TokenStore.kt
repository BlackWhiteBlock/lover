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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
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
    @Volatile private var currentAccessToken: String? = null
    @Volatile private var currentRefreshToken: String? = null
    @Volatile private var tokensWrittenInProcess = false
    @Volatile private var memoryState: AppState = AppState()

    val state = context.sessionDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            preferences[stateKey]
                ?.let { runCatching { json.decodeFromString<AppState>(it) }.getOrNull() }
                ?: AppState()
        }
        .map {
            it.copy(sessionLoaded = true)
        }
        .onEach {
            memoryState = it
            if (!tokensWrittenInProcess) {
                currentAccessToken = it.accessToken
                currentRefreshToken = it.refreshToken
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, AppState())

    val snapshot: AppState
        get() = memoryState.copy(
            accessToken = if (tokensWrittenInProcess) currentAccessToken else currentAccessToken ?: memoryState.accessToken,
            refreshToken = if (tokensWrittenInProcess) currentRefreshToken else currentRefreshToken ?: memoryState.refreshToken,
            sessionLoaded = true,
        )

    suspend fun update(transform: (AppState) -> AppState) {
        context.sessionDataStore.edit { preferences ->
            val persisted = preferences[stateKey]
                ?.let { runCatching { json.decodeFromString<AppState>(it) }.getOrNull() }
            // 反序列化失败时用内存态，避免把已写入的绑定邀请冲成空 AppState
            val current = persisted ?: memoryState
            val next = transform(current).copy(loading = false)
            memoryState = next.copy(sessionLoaded = true)
            preferences[stateKey] = json.encodeToString(next)
        }
    }

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
        tokensWrittenInProcess = true
        currentAccessToken = null
        currentRefreshToken = null
        memoryState = AppState(sessionLoaded = true)
        // 写成空状态（不只 remove），保证 StateFlow 立即发布 accessToken=null，UI 回登录页
        context.sessionDataStore.edit { preferences ->
            preferences[stateKey] = json.encodeToString(AppState())
        }
    }
}
