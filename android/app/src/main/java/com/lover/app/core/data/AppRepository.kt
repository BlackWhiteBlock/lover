package com.lover.app.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lover.app.core.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.loverDataStore by preferencesDataStore("lover_state")

@Singleton
class AppRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    private val stateKey = stringPreferencesKey("state")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val state = context.loverDataStore.data
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { preferences ->
            preferences[stateKey]?.let { runCatching { json.decodeFromString<LocalState>(it) }.getOrNull() }
                ?: LocalState()
        }
        .stateIn(scope, SharingStarted.Eagerly, LocalState())

    private suspend fun update(transform: (LocalState) -> LocalState) {
        context.loverDataStore.edit { preferences ->
            val current = preferences[stateKey]
                ?.let { runCatching { json.decodeFromString<LocalState>(it) }.getOrNull() }
                ?: LocalState()
            preferences[stateKey] = json.encodeToString(transform(current))
        }
    }

    suspend fun login(phone: String, code: String) {
        require(phone.length >= 6) { "请输入有效手机号" }
        require(code.length >= 4) { "请输入验证码" }
        update {
            it.copy(
                token = "dev-${UUID.randomUUID()}",
                user = User(id = UUID.randomUUID().toString(), phone = phone, nickname = "小恋"),
            )
        }
    }

    suspend fun createInvite(togetherDate: String, inviteCode: String = UUID.randomUUID().toString().take(6).uppercase()) {
        update {
            it.copy(
                couple = CoupleSpace(
                    id = UUID.randomUUID().toString(),
                    inviteCode = inviteCode,
                    togetherDate = togetherDate,
                    establishedAt = LocalDate.now().toString(),
                ),
            )
        }
    }

    suspend fun bindInvite(code: String, togetherDate: String) {
        require(code.trim().length >= 4) { "邀请码无效" }
        update {
            it.copy(
                couple = CoupleSpace(
                    id = UUID.randomUUID().toString(),
                    inviteCode = code.uppercase(),
                    partnerNickname = "恋人",
                    togetherDate = togetherDate,
                    establishedAt = LocalDate.now().toString(),
                ),
            )
        }
    }

    suspend fun addMedia(uri: String, type: MediaType, caption: String, date: String) = update {
        it.copy(
            media = listOf(
                MediaItem(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    url = uri,
                    caption = caption.trim(),
                    mediaDate = date,
                ),
            ) + it.media,
        )
    }

    suspend fun addAnniversary(title: String, date: String, type: AnniversaryType) = update {
        it.copy(
            anniversaries = listOf(
                Anniversary(UUID.randomUUID().toString(), title.trim(), date, type),
            ) + it.anniversaries,
        )
    }

    suspend fun addLetter(
        title: String,
        content: String,
        type: LetterType,
        unlockDate: String?,
    ) = update {
        it.copy(
            letters = listOf(
                Letter(
                    id = UUID.randomUUID().toString(),
                    sender = it.user?.nickname ?: "我",
                    title = title.trim(),
                    content = content.trim(),
                    type = type,
                    unlockDate = unlockDate,
                    createdDate = LocalDate.now().toString(),
                ),
            ) + it.letters,
        )
    }

    suspend fun logout() = update { LocalState() }
    suspend fun unbind() = update { it.copy(couple = null, media = emptyList(), anniversaries = emptyList(), letters = emptyList()) }
}
