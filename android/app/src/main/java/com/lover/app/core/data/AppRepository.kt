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
            if (tokenStore.snapshot.profileCompleted) {
                refreshAll()
            } else {
                tokenStore.update { it.copy(sessionReady = true) }
            }
        } catch (error: Throwable) {
            tokenStore.clearSession()
            throw error
        }
    }

    suspend fun restoreSession() {
        if (tokenStore.snapshot.accessToken == null) return
        tokenStore.update { it.copy(sessionReady = false) }
        try {
            applyMe(call { api.me() }, clearContent = false)
            if (tokenStore.snapshot.profileCompleted) {
                refreshAll()
            } else {
                tokenStore.update { it.copy(sessionReady = true) }
            }
        } catch (error: Throwable) {
            // 失败时仍放行主界面（展示缓存），避免一直转圈
            tokenStore.update { it.copy(sessionReady = true) }
            throw error
        }
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
                pendingIncomingBinds = if (clearContent || !me.profileCompleted) emptyList() else it.pendingIncomingBinds,
                pendingOutgoingBind = if (clearContent || !me.profileCompleted) null else it.pendingOutgoingBind,
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
        tokenStore.update { it.copy(sessionReady = false) }
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
        val created = call { api.createBind(CreateBindRequest(phone.trim())) }
        val outgoing = OutgoingBindRequest(
            id = created.id,
            targetUserId = created.targetUserId.orEmpty(),
            targetNickname = created.targetNickname.orEmpty(),
            targetPhone = created.targetPhone?.ifBlank { null } ?: phone.trim(),
            status = created.status.ifBlank { "pending" },
            expiresAt = created.expiresAt,
            createdAt = created.createdAt,
        )
        // 先本地落出发状态，避免 refresh 慢/失败时界面回到「绑定另一半」
        tokenStore.update {
            it.copy(
                pendingOutgoingBind = outgoing,
                couple = (it.couple ?: CoupleSpace()).copy(pendingOutgoingBind = outgoing),
            )
        }
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
        tokenStore.update {
            it.copy(
                pendingOutgoingBind = null,
                couple = it.couple?.copy(pendingOutgoingBind = null),
            )
        }
        refreshAll()
    }

    suspend fun updateTogetherDate(togetherDate: String?) {
        call { api.updateCoupleLink(UpdateCoupleLinkRequest(togetherDate = togetherDate)) }
        refreshAll()
    }

    suspend fun refreshAll() = coroutineScope {
        val bootstrapDeferred = async { runCatching { call { api.bootstrap() } }.getOrNull() }
        val coupleDeferred = async { runCatching { call { api.coupleSpace() } }.getOrNull() }
        val pendingDeferred = async { runCatching { call { api.pendingBinds() } }.getOrNull() }
        val mediaDeferred = async {
            runCatching { signMedia(call { api.media().items }) }.getOrDefault(emptyList())
        }
        val anniversariesDeferred = async {
            runCatching { call { api.anniversaries().items } }.getOrDefault(emptyList())
        }
        val lettersDeferred = async {
            runCatching { call { api.letters().items } }.getOrDefault(emptyList())
        }

        val bootstrap = bootstrapDeferred.await()
        val coupleBase = coupleDeferred.await()
        val pending = pendingDeferred.await()
        val media = mediaDeferred.await()
        val anniversaries = anniversariesDeferred.await()
        val letters = lettersDeferred.await()

        if (bootstrap == null && coupleBase == null && pending == null) {
            error("刷新失败，请检查网络后重试")
        }

        // pending == null 表示接口失败，保留本地；pending != null 则以服务端为准（含空列表）
        val previous = tokenStore.snapshot
        val incoming = when {
            pending != null -> pending.incoming
            coupleBase != null -> coupleBase.pendingIncomingBinds
            else -> previous.pendingIncomingBinds
        }
        val outgoing = when {
            pending != null -> pending.outgoing.firstOrNull()
            coupleBase?.pendingOutgoingBind != null -> coupleBase.pendingOutgoingBind
            else -> previous.pendingOutgoingBind
        }

        val couple = when {
            coupleBase != null -> coupleBase.copy(
                pendingIncomingBinds = incoming,
                pendingOutgoingBind = outgoing,
            )
            pending != null -> CoupleSpace(
                pendingIncomingBinds = incoming,
                pendingOutgoingBind = outgoing,
            )
            else -> previous.couple?.copy(
                pendingIncomingBinds = incoming,
                pendingOutgoingBind = outgoing,
            )
        }

        // 一次原子写入：绑定状态 + 列表，避免二次 update 冲掉邀请
        tokenStore.update {
            it.copy(
                activeSpaceId = bootstrap?.space?.id ?: couple?.id ?: it.activeSpaceId,
                personalSpaceId = couple?.personalSpaceId ?: it.personalSpaceId,
                loverSpaceId = couple?.loverSpaceId ?: it.loverSpaceId,
                linked = bootstrap?.linked == true || couple?.linked == true,
                couple = couple,
                pendingIncomingBinds = incoming,
                pendingOutgoingBind = outgoing,
                lovingDays = bootstrap?.lovingJourney?.days ?: it.lovingDays,
                needsTogetherDate = bootstrap?.lovingJourney?.needsTogetherDate ?: it.needsTogetherDate,
                media = media,
                anniversaries = anniversaries,
                letters = letters,
                sessionReady = true,
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

    suspend fun updateMedia(id: String, caption: String, date: String) {
        call { api.updateMedia(id, UpdateMediaRequest(caption.trim(), date)) }
        refreshAll()
    }

    suspend fun deleteMedia(id: String) {
        call { api.deleteMedia(id) }
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
        unlockOnPartnerBind: Boolean = false,
    ) {
        val unlockAt = when {
            type != LetterType.CAPSULE -> null
            unlockOnPartnerBind -> null
            else -> unlockDate?.let { localMidnightWithOffset(it) }
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
        refreshAll()
    }

    suspend fun cancelUnbinding(id: String) {
        call { api.cancelUnbinding(id) }
        refreshAll()
    }

    suspend fun logout() {
        runCatching { call { api.logout() } }
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
