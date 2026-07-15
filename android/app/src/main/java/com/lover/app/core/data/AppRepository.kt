package com.lover.app.core.data

import android.net.Uri
import com.lover.app.core.media.ContentMediaResolver
import com.lover.app.core.media.ResolvedMedia
import com.lover.app.core.media.VideoThumbnailExtractor
import com.lover.app.core.model.*
import com.lover.app.core.network.ApiService
import com.lover.app.core.network.AssetUploader
import com.lover.app.core.network.isUnauthorized
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
            applyMe(call { api.me() }, clearContent = true)
            if (tokenStore.snapshot.profileCompleted) refreshAll()
        } catch (error: Throwable) {
            tokenStore.clearSession()
            throw error
        }
    }

    suspend fun restoreSession() {
        if (tokenStore.snapshot.accessToken == null) return
        applyMe(call { api.me() }, clearContent = false)
        if (tokenStore.snapshot.profileCompleted) refreshAll()
    }

    private suspend fun applyMe(me: MeResponse, clearContent: Boolean) {
        tokenStore.update {
            it.copy(
                user = me.user,
                activeSpaceId = me.activeSpaceId,
                personalSpaceId = me.personalSpaceId,
                loverSpaceId = me.loverSpaceId,
                profileCompleted = me.profileCompleted || me.user.profileCompleted,
                linked = me.linked,
                couple = if (clearContent || !me.profileCompleted) null else it.couple,
                lovingDays = if (clearContent || !me.profileCompleted) null else it.lovingDays,
                media = if (clearContent || !me.profileCompleted) emptyList() else it.media,
                anniversaries = if (clearContent || !me.profileCompleted) emptyList() else it.anniversaries,
                letters = if (clearContent || !me.profileCompleted) emptyList() else it.letters,
            )
        }
    }

    suspend fun completeOnboarding(
        nickname: String,
        gender: String,
        birthday: String,
        spaceName: String,
        avatarUri: Uri? = null,
    ) {
        val result = call {
            api.onboarding(
                OnboardingRequest(
                    nickname = nickname.trim(),
                    avatarUrl = null,
                    gender = gender,
                    birthday = birthday,
                    spaceName = spaceName.trim(),
                ),
            )
        }
        var user = result.user
        tokenStore.update {
            it.copy(
                user = user,
                personalSpaceId = result.personalSpaceId,
                activeSpaceId = result.personalSpaceId,
                profileCompleted = true,
                linked = false,
            )
        }
        // 空间创建后才能上传私有媒体；有选择头像时再写入 avatar_asset_id
        var avatarError: Throwable? = null
        if (avatarUri != null) {
            runCatching {
                val assetId = uploadAvatar(avatarUri)
                user = call { api.patchMe(PatchMeRequest(assetId)) }.user
                tokenStore.update { it.copy(user = user) }
            }.onFailure { avatarError = it }
        }
        refreshAll()
        avatarError?.let { throw IllegalStateException(it.message ?: "头像上传失败，可稍后在资料里重试") }
    }

    suspend fun requestBind(phone: String) {
        call { api.createBind(CreateBindRequest(phone.trim())) }
        refreshAll()
    }

    suspend fun acceptBind(id: String): AcceptBindResponse {
        val result = call { api.acceptBind(id) }
        refreshAll()
        return result
    }

    suspend fun rejectBind(id: String) {
        call { api.rejectBind(id) }
        refreshAll()
    }

    suspend fun cancelBind(id: String) {
        call { api.cancelBind(id) }
        refreshAll()
    }

    suspend fun updateTogetherDate(togetherDate: String?) {
        call { api.updateCoupleLink(UpdateCoupleLinkRequest(togetherDate = togetherDate)) }
        refreshAll()
    }

    suspend fun refreshAll() = coroutineScope {
        val bootstrapDeferred = async { call { api.bootstrap() } }
        val coupleDeferred = async { call { api.coupleSpace() } }
        val mediaDeferred = async { call { api.media().items } }
        val anniversariesDeferred = async { call { api.anniversaries().items } }
        val lettersDeferred = async { call { api.letters().items } }
        val bootstrap = bootstrapDeferred.await()
        val couple = coupleDeferred.await()
        val media = signMedia(mediaDeferred.await())
        val anniversaries = anniversariesDeferred.await()
        val letters = lettersDeferred.await()
        tokenStore.update {
            it.copy(
                activeSpaceId = bootstrap.space.id,
                personalSpaceId = couple.personalSpaceId ?: it.personalSpaceId,
                loverSpaceId = couple.loverSpaceId,
                linked = bootstrap.linked || couple.linked,
                couple = couple,
                lovingDays = bootstrap.lovingJourney.days,
                needsTogetherDate = bootstrap.lovingJourney.needsTogetherDate,
                media = media,
                anniversaries = anniversaries,
                letters = letters,
            )
        }
    }

    private suspend fun signMedia(items: List<MediaItem>): List<MediaItem> = coroutineScope {
        items.map { item ->
            async {
                val signedAssets = item.assets.map { part ->
                    async {
                        val assetUrl = runCatching { call { api.signAsset(part.assetId).url } }.getOrDefault("")
                        val thumbUrl = part.thumbnailAssetId?.let { assetId ->
                            runCatching { call { api.signAsset(assetId).url } }.getOrNull()
                        }
                        part.copy(url = assetUrl, thumbnailUrl = thumbUrl)
                    }
                }.awaitAll()
                item.copy(assets = signedAssets)
            }
        }.awaitAll()
    }

    suspend fun addMedia(uri: Uri, caption: String, date: String) {
        addMediaBatch(listOf(uri), caption, date)
    }

    /**
     * Uploads all selected media, then creates **one** timeline record with ordered assets.
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
        val assets = uris.mapIndexed { index, uri ->
            onProgress(index + 1, uris.size)
            uploadMediaPart(uri)
        }
        call {
            api.createMedia(
                CreateMediaRequest(
                    caption = trimmed,
                    mediaDate = date,
                    assets = assets,
                ),
            )
        }
        refreshAll()
    }

    private suspend fun uploadMediaPart(uri: Uri): CreateMediaAssetRequest {
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
        return CreateMediaAssetRequest(
            type = if (source.isVideo) MediaType.VIDEO else MediaType.IMAGE,
            assetId = assetId,
            thumbnailAssetId = thumbnailAssetId,
        )
    }

    suspend fun addAnniversary(title: String, date: String, type: AnniversaryType) {
        call {
            api.createAnniversary(
                CreateAnniversaryRequest(title.trim(), date, type),
            )
        }
        refreshAll()
    }

    suspend fun updateMedia(id: String, caption: String, date: String) {
        LocalDate.parse(date)
        call {
            api.updateMedia(
                id,
                UpdateMediaRequest(
                    caption = caption.trim(),
                    mediaDate = date,
                ),
            )
        }
        refreshAll()
    }

    suspend fun deleteMedia(id: String) {
        call { api.deleteMedia(id) }
        refreshAll()
    }

    suspend fun addLetter(
        title: String,
        content: String,
        type: LetterType,
        unlockDate: String?,
        unlockOnPartnerBind: Boolean = false,
    ) {
        val unlockAt = when {
            type != LetterType.CAPSULE -> null
            unlockOnPartnerBind -> null
            else -> localMidnightWithOffset(requireNotNull(unlockDate))
        }
        call {
            api.createLetter(
                CreateLetterRequest(
                    title = title.trim(),
                    content = content.trim(),
                    type = type,
                    unlockAt = unlockAt,
                    unlockOnPartnerBind = unlockOnPartnerBind,
                ),
            )
        }
        refreshAll()
    }

    suspend fun requestUnbinding(reason: String?) {
        call { api.requestUnbinding(CreateUnbindingRequest(reason?.trim()?.ifBlank { null })) }
        refreshAll()
    }

    suspend fun confirmUnbinding(id: String) {
        call { api.confirmUnbinding(id) }
        applyMe(call { api.me() }, clearContent = true)
        if (tokenStore.snapshot.profileCompleted) refreshAll()
    }

    suspend fun cancelUnbinding(id: String) {
        call { api.cancelUnbinding(id) }
        refreshAll()
    }

    suspend fun logout() {
        runCatching { call { api.logout() } }
        // 无论服务端是否仍接受旧 token，本地都结束会话
        tokenStore.clearSession()
    }

    private suspend fun uploadAvatar(uri: Uri): String {
        val source = withContext(Dispatchers.IO) { mediaResolver.resolve(uri) }
        require(!source.isVideo) { "头像仅支持图片" }
        return uploadAsset(source, purpose = "avatar")
    }

    private suspend fun uploadAsset(source: ResolvedMedia, purpose: String = "media"): String =
        uploadAsset(source.fileName, source.mimeType, source.sizeBytes, source.body, purpose)

    private suspend fun uploadAsset(
        fileName: String,
        mimeType: String,
        sizeBytes: Long,
        body: RequestBody,
        purpose: String = "media",
    ): String {
        val token = call {
            api.assetToken(TokenAssetRequest(fileName, mimeType, sizeBytes, purpose))
        }
        call { assetUploader.upload(token, fileName, body) }
        call { api.completeAsset(AssetRequest(token.assetId)) }
        return token.assetId
    }

    private suspend fun <T> call(block: suspend () -> T): T =
        try {
            block()
        } catch (error: Throwable) {
            val facing = error.toUserFacing(json)
            // 鉴权失败：清掉本地会话，MainActivity 会切回登录页
            if (facing.isUnauthorized()) {
                runCatching { tokenStore.clearSession() }
            }
            throw facing
        }

    companion object {
        fun localMidnightWithOffset(date: String, zoneId: ZoneId = ZoneId.systemDefault()): String =
            LocalDate.parse(date)
                .atStartOfDay(zoneId)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }
}
