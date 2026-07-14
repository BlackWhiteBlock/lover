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
            if (!tokensWrittenInProcess) {
                currentAccessToken = it.accessToken
                currentRefreshToken = it.refreshToken
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, AppState())

    val snapshot: AppState
        get() = state.value.copy(
            accessToken = if (tokensWrittenInProcess) currentAccessToken else currentAccessToken ?: state.value.accessToken,
            refreshToken = if (tokensWrittenInProcess) currentRefreshToken else currentRefreshToken ?: state.value.refreshToken,
        )

    suspend fun update(transform: (AppState) -> AppState) {
        context.sessionDataStore.edit { preferences ->
            val current = preferences[stateKey]
                ?.let { runCatching { json.decodeFromString<AppState>(it) }.getOrNull() }
                ?: AppState()
            preferences[stateKey] = json.encodeToString(transform(current).copy(loading = false))
        }
    }

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        tokensWrittenInProcess = true
        currentAccessToken = accessToken
        currentRefreshToken = refreshToken
        update { it.copy(accessToken = accessToken, refreshToken = refreshToken) }
    }

    suspend fun clearSession() {
        tokensWrittenInProcess = true
        currentAccessToken = null
        currentRefreshToken = null
        context.sessionDataStore.edit { it.remove(stateKey) }
    }
}
