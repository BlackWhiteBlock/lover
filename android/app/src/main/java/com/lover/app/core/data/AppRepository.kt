package com.lover.app.core.data

import android.net.Uri
import com.lover.app.core.media.ContentMediaResolver
import com.lover.app.core.media.ResolvedMedia
import com.lover.app.core.media.VideoThumbnailExtractor
import com.lover.app.core.model.*
import com.lover.app.core.network.ApiService
import com.lover.app.core.network.AssetUploader
import com.lover.app.core.network.toUserFacing
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class AppRepository @Inject constructor(
    private val json: Json,
    private val api: ApiService,
    private val assetUploader: AssetUploader,
    private val mediaResolver: ContentMediaResolver,
    private val thumbnailExtractor: VideoThumbnailExtractor,
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

    suspend fun addMedia(uri: Uri, caption: String, date: String) {
        addMediaBatch(listOf(uri), caption, date)
    }

    /**
     * Uploads items in reverse of [uris] so that a feed sorted by `created_at desc`
     * matches the left-to-right order the user arranged.
     */
    suspend fun addMediaBatch(
        uris: List<Uri>,
        caption: String,
        date: String,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
    ) {
        require(uris.isNotEmpty()) { "请选择至少一张照片或视频" }
        LocalDate.parse(date)
        val trimmed = caption.trim()
        val uploadOrder = uris.asReversed()
        uploadOrder.forEachIndexed { index, uri ->
            onProgress(index + 1, uris.size)
            createMediaItem(uri, trimmed, date)
        }
        refreshAll()
    }

    private suspend fun createMediaItem(uri: Uri, caption: String, date: String) {
        val source = withContext(Dispatchers.IO) { mediaResolver.resolve(uri) }
        val thumbnailAssetId = if (source.isVideo) {
            val thumbnail = withContext(Dispatchers.IO) {
                thumbnailExtractor.extract(source.uri, source.fileName)
            }
            uploadAsset(
                fileName = thumbnail.fileName,
                mimeType = "image/jpeg",
                sizeBytes = thumbnail.bytes.size.toLong(),
                body = thumbnail.bytes.toRequestBody("image/jpeg".toMediaType()),
            )
        } else {
            null
        }
        val assetId = uploadAsset(source)
        call {
            api.createMedia(
                CreateMediaRequest(
                    type = if (source.isVideo) MediaType.VIDEO else MediaType.IMAGE,
                    assetId = assetId,
                    thumbnailAssetId = thumbnailAssetId,
                    caption = caption,
                    mediaDate = date,
                ),
            )
        }
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

    private suspend fun uploadAsset(source: ResolvedMedia): String =
        uploadAsset(source.fileName, source.mimeType, source.sizeBytes, source.body)

    private suspend fun uploadAsset(
        fileName: String,
        mimeType: String,
        sizeBytes: Long,
        body: RequestBody,
    ): String {
        val token = call { api.assetToken(TokenAssetRequest(fileName, mimeType, sizeBytes)) }
        call { assetUploader.upload(token, fileName, body) }
        call { api.completeAsset(AssetRequest(token.assetId)) }
        return token.assetId
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
