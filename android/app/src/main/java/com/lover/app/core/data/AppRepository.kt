package com.lover.app.core.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.lover.app.core.model.*
import com.lover.app.core.network.ApiService
import com.lover.app.core.network.AssetUploader
import com.lover.app.core.network.toUserFacing
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json

@Singleton
class AppRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
    private val api: ApiService,
    private val assetUploader: AssetUploader,
    private val tokenStore: TokenStore,
) {
    val state = tokenStore.state

    suspend fun sendSms(phone: String): SendSmsResponse =
        call { api.sendSms(SendSmsRequest(phone.trim())) }

    suspend fun login(phone: String, code: String) {
        val auth = call { api.login(LoginRequest(phone.trim(), code.trim())) }
        tokenStore.saveTokens(auth.accessToken, auth.refreshToken)
        try {
            val me = call { api.me() }
            tokenStore.update {
                it.copy(
                    user = me.user,
                    activeSpaceId = me.activeSpaceId,
                    couple = null,
                    lovingDays = null,
                    media = emptyList(),
                    anniversaries = emptyList(),
                    letters = emptyList(),
                )
            }
            if (me.activeSpaceId != null) refreshAll()
        } catch (error: Throwable) {
            tokenStore.clearSession()
            throw error
        }
    }

    suspend fun restoreSession() {
        if (tokenStore.snapshot.accessToken == null) return
        val me = call { api.me() }
        tokenStore.update {
            it.copy(
                user = me.user,
                activeSpaceId = me.activeSpaceId,
                couple = if (me.activeSpaceId == null) null else it.couple,
                lovingDays = if (me.activeSpaceId == null) null else it.lovingDays,
                media = if (me.activeSpaceId == null) emptyList() else it.media,
                anniversaries = if (me.activeSpaceId == null) emptyList() else it.anniversaries,
                letters = if (me.activeSpaceId == null) emptyList() else it.letters,
            )
        }
        if (me.activeSpaceId != null) refreshAll()
    }

    suspend fun createInvite(togetherDate: String): String {
        val invite = call { api.createInvite(CreateInviteRequest(togetherDate = togetherDate)) }
        tokenStore.update { it.copy(activeSpaceId = invite.spaceId) }
        refreshAll(invite.code)
        return invite.code
    }

    suspend fun acceptInvite(code: String) {
        val accepted = call { api.acceptInvite(AcceptInviteRequest(code.trim().uppercase())) }
        tokenStore.update { it.copy(activeSpaceId = accepted.spaceId) }
        refreshAll()
    }

    suspend fun refreshAll(inviteCode: String? = tokenStore.snapshot.couple?.inviteCode) = coroutineScope {
        val bootstrapDeferred = async { call { api.bootstrap() } }
        val coupleDeferred = async { call { api.coupleSpace() } }
        val mediaDeferred = async { call { api.media().items } }
        val anniversariesDeferred = async { call { api.anniversaries().items } }
        val lettersDeferred = async { call { api.letters().items } }
        val bootstrap = bootstrapDeferred.await()
        val couple = coupleDeferred.await().copy(inviteCode = inviteCode)
        val media = signMedia(mediaDeferred.await())
        val anniversaries = anniversariesDeferred.await()
        val letters = lettersDeferred.await()
        tokenStore.update {
            it.copy(
                activeSpaceId = bootstrap.space.id,
                couple = couple,
                lovingDays = bootstrap.lovingJourney.days,
                media = media,
                anniversaries = anniversaries,
                letters = letters,
            )
        }
    }

    private suspend fun signMedia(items: List<MediaItem>): List<MediaItem> = coroutineScope {
        items.map { item ->
            async {
                val assetUrl = runCatching { call { api.signAsset(item.assetId).url } }.getOrDefault("")
                val thumbUrl = item.thumbnailAssetId?.let { assetId ->
                    runCatching { call { api.signAsset(assetId).url } }.getOrNull()
                }
                item.copy(url = assetUrl, thumbnailUrl = thumbUrl)
            }
        }.awaitAll()
    }

    suspend fun addImage(uri: Uri, caption: String, date: String) {
        LocalDate.parse(date)
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: throw IllegalArgumentException("无法识别图片类型")
        require(mimeType in setOf("image/jpeg", "image/png", "image/webp", "image/heic")) {
            "暂仅支持 JPEG、PNG、WebP 与 HEIC 图片"
        }
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("无法读取所选图片")
        require(bytes.isNotEmpty()) { "图片内容为空" }
        require(bytes.size <= 30 * 1024 * 1024) { "图片不能超过 30 MB" }
        val fileName = queryFileName(uri) ?: "lover-${System.currentTimeMillis()}.${extension(mimeType)}"
        val token = call { api.assetToken(TokenAssetRequest(fileName, mimeType, bytes.size.toLong())) }
        call { assetUploader.upload(token, fileName, mimeType, bytes) }
        call { api.completeAsset(AssetRequest(token.assetId)) }
        call {
            api.createMedia(
                CreateMediaRequest(
                    type = MediaType.IMAGE,
                    assetId = token.assetId,
                    caption = caption.trim(),
                    mediaDate = date,
                ),
            )
        }
        refreshAll()
    }

    suspend fun addAnniversary(title: String, date: String, type: AnniversaryType) {
        call {
            api.createAnniversary(
                CreateAnniversaryRequest(title.trim(), date, type),
            )
        }
        refreshAll()
    }

    suspend fun addLetter(
        title: String,
        content: String,
        type: LetterType,
        unlockDate: String?,
    ) {
        val unlockAt = if (type == LetterType.CAPSULE) {
            localMidnightWithOffset(requireNotNull(unlockDate))
        } else null
        call { api.createLetter(CreateLetterRequest(title.trim(), content.trim(), type, unlockAt)) }
        refreshAll()
    }

    suspend fun requestUnbinding(reason: String?) {
        call { api.requestUnbinding(CreateUnbindingRequest(reason?.trim()?.ifBlank { null })) }
        refreshAll()
    }

    suspend fun confirmUnbinding(id: String) {
        call { api.confirmUnbinding(id) }
        val me = call { api.me() }
        tokenStore.update {
            it.copy(
                activeSpaceId = me.activeSpaceId,
                couple = null,
                lovingDays = null,
                media = emptyList(),
                anniversaries = emptyList(),
                letters = emptyList(),
            )
        }
    }

    suspend fun cancelUnbinding(id: String) {
        call { api.cancelUnbinding(id) }
        refreshAll()
    }

    suspend fun logout() {
        val result = runCatching { call { api.logout() } }
        tokenStore.clearSession()
        result.getOrThrow()
    }

    private fun queryFileName(uri: Uri): String? {
        if (uri.scheme != "content") return uri.lastPathSegment
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }

    private fun extension(mimeType: String) = when (mimeType) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/heic" -> "heic"
        else -> "jpg"
    }

    private suspend fun <T> call(block: suspend () -> T): T =
        try {
            block()
        } catch (error: Throwable) {
            throw error.toUserFacing(json)
        }

    companion object {
        fun localMidnightWithOffset(date: String, zoneId: ZoneId = ZoneId.systemDefault()): String =
            LocalDate.parse(date)
                .atStartOfDay(zoneId)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }
}
