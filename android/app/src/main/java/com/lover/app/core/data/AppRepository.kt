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
                dailyQuote = if (clearContent || !me.profileCompleted) null else it.dailyQuote,
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
            targetAvatarUrl = created.targetAvatarUrl,
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
        tokenStore.update {
            it.copy(
                linked = true,
                coupleLinkId = result.coupleLinkId,
                loverSpaceId = result.loverSpaceId,
                needsTogetherDate = result.needsTogetherDate,
            )
        }
        refreshAll()
        return result
    }

    suspend fun markCoupleThemeRevealShown(linkId: String) {
        if (linkId.isBlank()) return
        tokenStore.update { it.copy(coupleThemeRevealShownLinkId = linkId) }
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

    /** 未绑定：改个人空间名 + 可选头像 */
    suspend fun updatePersonalCard(spaceName: String, avatarUri: Uri? = null) {
        val trimmed = spaceName.trim()
        require(trimmed.isNotEmpty()) { "请填写空间名称" }
        call { api.updateCoupleSpace(UpdateCoupleSpaceRequest(name = trimmed)) }
        if (avatarUri != null) {
            val assetId = uploadAvatar(avatarUri)
            val user = call { api.patchMe(PatchMeRequest(avatarAssetId = assetId)) }.user
            tokenStore.update { it.copy(user = user) }
        }
        refreshAll()
    }

    suspend fun lookupUser(phone: String): UserLookupResponse =
        call { api.lookupUser(phone.trim()) }

    /** 更新「我们」卡片：空间名/在一起日期双方同步；情侣合照仅本账号。 */
    suspend fun updateCoupleCard(
        name: String? = null,
        togetherDate: String? = null,
        coverUri: Uri? = null,
        clearCover: Boolean = false,
    ) {
        val trimmedName = name?.trim()?.takeIf { it.isNotEmpty() }
        if (trimmedName != null || togetherDate != null) {
            call {
                api.updateCoupleLink(
                    UpdateCoupleLinkRequest(
                        name = trimmedName,
                        togetherDate = togetherDate,
                    ),
                )
            }
        }
        when {
            clearCover -> {
                val user = call { api.patchMe(PatchMeRequest(clearCoupleCover = true)) }.user
                tokenStore.update { it.copy(user = user) }
            }
            coverUri != null -> {
                val assetId = uploadCoupleCover(coverUri)
                val user = call { api.patchMe(PatchMeRequest(coupleCoverAssetId = assetId)) }.user
                tokenStore.update { it.copy(user = user) }
            }
        }
        refreshAll()
    }

    suspend fun refreshAll() = coroutineScope {
        val meDeferred = async { runCatching { call { api.me() } }.getOrNull() }
        val bootstrapDeferred = async { runCatching { call { api.bootstrap() } }.getOrNull() }
        val coupleDeferred = async { runCatching { call { api.coupleSpace() } }.getOrNull() }
        val pendingDeferred = async { runCatching { call { api.pendingBinds() } }.getOrNull() }
        val previous = tokenStore.snapshot
        val mediaDeferred = async {
            runCatching {
                signMedia(sortMediaByRecordDate(call { api.media().items }), previous.media)
            }.getOrDefault(previous.media)
        }
        val anniversariesDeferred = async {
            runCatching { call { api.anniversaries().items } }.getOrDefault(emptyList())
        }
        val lettersDeferred = async {
            runCatching { call { api.letters().items } }.getOrDefault(emptyList())
        }

        val me = meDeferred.await()
        val bootstrap = bootstrapDeferred.await()
        val coupleBase = coupleDeferred.await()
        val pending = pendingDeferred.await()
        val media = mediaDeferred.await()
        val anniversaries = anniversariesDeferred.await()
        val letters = lettersDeferred.await()

        if (me == null && bootstrap == null && coupleBase == null && pending == null) {
            error("刷新失败，请检查网络后重试")
        }

        // pending == null 表示接口失败，保留本地；pending != null 则以服务端为准（含空列表）
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
                user = me?.user ?: it.user,
                activeSpaceId = me?.activeSpaceId
                    ?: bootstrap?.space?.id
                    ?: couple?.id
                    ?: it.activeSpaceId,
                personalSpaceId = me?.personalSpaceId
                    ?: couple?.personalSpaceId
                    ?: it.personalSpaceId,
                loverSpaceId = me?.loverSpaceId
                    ?: couple?.loverSpaceId
                    ?: it.loverSpaceId,
                linked = me?.linked == true || bootstrap?.linked == true || couple?.linked == true,
                coupleLinkId = bootstrap?.coupleLinkId
                    ?: couple?.coupleLinkId
                    ?: it.coupleLinkId,
                couple = couple,
                pendingIncomingBinds = incoming,
                pendingOutgoingBind = outgoing,
                lovingDays = bootstrap?.lovingJourney?.days ?: it.lovingDays,
                needsTogetherDate = bootstrap?.lovingJourney?.needsTogetherDate ?: it.needsTogetherDate,
                dailyQuote = bootstrap?.dailyQuote ?: it.dailyQuote,
                media = media,
                anniversaries = anniversaries,
                letters = letters,
                sessionReady = true,
            )
        }
    }

    /** 仅刷新时光列表，保留已有缩略图签名，避免整页重载闪白 */
    suspend fun refreshMedia() {
        val previous = tokenStore.snapshot.media
        val signed = runCatching {
            signMedia(sortMediaByRecordDate(call { api.media().items }), previous)
        }.getOrDefault(previous)
        tokenStore.update { it.copy(media = signed) }
    }

    private fun sortMediaByRecordDate(items: List<MediaItem>): List<MediaItem> =
        items.sortedWith(
            compareByDescending<MediaItem> { it.mediaDate }
                .thenByDescending { it.createdAt },
        )

    private suspend fun signMedia(
        items: List<MediaItem>,
        previous: List<MediaItem> = emptyList(),
    ): List<MediaItem> = coroutineScope {
        val previousById = previous.associateBy { it.id }
        items.map { item ->
            async {
                val prev = previousById[item.id]
                if (prev != null && canReuseSignedThumbs(item, prev)) {
                    mergeSignedThumbs(item, prev)
                } else {
                    val signedAssets = item.assets.map { part ->
                        async { signAssetForList(part) }
                    }.awaitAll()
                    item.copy(assets = signedAssets)
                }
            }
        }.awaitAll()
    }

    private fun mediaAssetFingerprint(assets: List<MediaAssetPart>): List<String> =
        assets.sortedBy { it.sortOrder }.map { part ->
            "${part.id}:${part.assetId}:${part.type.name}:${part.sortOrder}"
        }

    private fun canReuseSignedThumbs(item: MediaItem, prev: MediaItem): Boolean {
        if (mediaAssetFingerprint(item.assets) != mediaAssetFingerprint(prev.assets)) return false
        return prev.assets.any { isReusableSignedUrl(it.thumbnailUrl) || isReusableSignedUrl(it.url) }
    }

    private fun mergeSignedThumbs(item: MediaItem, prev: MediaItem): MediaItem {
        val prevByPartId = prev.assets.filter { it.id.isNotBlank() }.associateBy { it.id }
        val prevByAssetId = prev.assets.associateBy { it.assetId }
        val merged = item.assets.map { part ->
            val cached = prevByPartId[part.id] ?: prevByAssetId[part.assetId]
            val thumb = cached?.thumbnailUrl?.takeIf(::isReusableSignedUrl)
            val original = cached?.url?.takeIf(::isReusableSignedUrl).orEmpty()
            if (thumb != null || original.isNotBlank()) {
                part.copy(url = original, thumbnailUrl = thumb)
            } else {
                part
            }
        }
        return item.copy(assets = merged)
    }

    // Do not reuse cleartext or Qiniu test-domain signed URLs after CDN domain switch.
    private fun isReusableSignedUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (!url.startsWith("https://", ignoreCase = true)) return false
        val lower = url.lowercase()
        return "clouddn.com" !in lower && "qiniucdn.com" !in lower
    }

    /** 列表/掠影：只签缩略图，避免刷屏拉原图；thumb 失败时回退原图，避免迁移后整页空白 */
    private suspend fun signAssetForList(part: MediaAssetPart): MediaAssetPart {
        val thumbUrl = runCatching {
            when {
                part.type == MediaType.VIDEO && !part.thumbnailAssetId.isNullOrBlank() ->
                    call { api.signAsset(part.thumbnailAssetId!!, SignAssetRequest("original")) }.url
                else -> {
                    runCatching {
                        call { api.signAsset(part.assetId, SignAssetRequest("thumb")) }.url
                    }.getOrElse {
                        call { api.signAsset(part.assetId, SignAssetRequest("original")) }.url
                    }
                }
            }
        }.getOrNull()
        return part.copy(url = "", thumbnailUrl = thumbUrl)
    }

    /** 详情/播放：按需签发原图（或视频原文件） */
    suspend fun ensureMediaOriginals(item: MediaItem): MediaItem = coroutineScope {
        val signed = item.assets.map { part ->
            async {
                if (part.url.isNotBlank()) return@async part
                val url = runCatching {
                    call { api.signAsset(part.assetId, SignAssetRequest("original")) }.url
                }.getOrDefault("")
                part.copy(url = url)
            }
        }.awaitAll()
        item.copy(assets = signed)
    }

    suspend fun addMedia(uri: Uri, caption: String, date: String) {
        addMediaBatch(listOf(uri), caption, date)
    }

    data class MediaUploadProgress(
        val completedFiles: Int,
        val totalFiles: Int,
        /** 当前文件 0~1 */
        val fileFraction: Float,
        val phase: String,
    )

    suspend fun addMediaBatch(
        uris: List<Uri>,
        caption: String,
        date: String,
        onProgress: (MediaUploadProgress) -> Unit = {},
    ) {
        require(uris.isNotEmpty()) { "请选择至少一张照片或视频" }
        LocalDate.parse(date)
        val trimmed = caption.trim()
        onProgress(MediaUploadProgress(0, uris.size, 0f, "准备上传"))
        val assets = ArrayList<CreateMediaAssetRequest>(uris.size)
        uris.forEachIndexed { index, uri ->
            assets += uploadMediaPart(uri) { fraction, phase ->
                onProgress(MediaUploadProgress(index, uris.size, fraction, phase))
            }
            onProgress(MediaUploadProgress(index + 1, uris.size, 1f, "本项已完成"))
        }
        onProgress(MediaUploadProgress(uris.size, uris.size, 1f, "正在保存"))
        call {
            api.createMedia(
                CreateMediaRequest(
                    caption = trimmed,
                    mediaDate = date,
                    assets = assets,
                ),
            )
        }
        refreshMedia()
    }

    private suspend fun uploadMediaPart(
        uri: Uri,
        onPartProgress: (fraction: Float, phase: String) -> Unit = { _, _ -> },
    ): CreateMediaAssetRequest {
        onPartProgress(0.02f, "读取文件")
        val source = withContext(Dispatchers.IO) { mediaResolver.resolve(uri) }
        val thumbnailAssetId = if (source.isVideo) {
            onPartProgress(0.05f, "生成封面")
            val thumbnail = withContext(Dispatchers.IO) {
                thumbnailExtractor.extract(source.uri, source.fileName)
            }
            uploadAsset(
                fileName = thumbnail.fileName,
                mimeType = "image/jpeg",
                sizeBytes = thumbnail.bytes.size.toLong(),
                body = thumbnail.bytes.toRequestBody("image/jpeg".toMediaType()),
                onBytes = { written, total ->
                    val f = if (total > 0) written.toFloat() / total else 0f
                    onPartProgress(0.08f + f * 0.12f, "上传封面")
                },
            )
        } else {
            null
        }
        val base = if (source.isVideo) 0.22f else 0.05f
        val span = if (source.isVideo) 0.72f else 0.9f
        onPartProgress(base, "上传媒体")
        val assetId = uploadAsset(source, onBytes = { written, total ->
            val f = if (total > 0) written.toFloat() / total else 0f
            onPartProgress(base + f * span, "上传媒体")
        })
        onPartProgress(0.98f, "确认文件")
        return CreateMediaAssetRequest(
            type = if (source.isVideo) MediaType.VIDEO else MediaType.IMAGE,
            assetId = assetId,
            thumbnailAssetId = thumbnailAssetId,
        )
    }

    /**
     * @param assetOrder 最终媒体顺序（含保留的 partId 与待上传的本地 Uri）。
     * 为 null 时仅更新文案/日期；非 null 时以该顺序全量同步媒体。
     */
    suspend fun updateMedia(
        id: String,
        caption: String,
        date: String? = null,
        assetOrder: List<MediaEditOrderItem>? = null,
    ) {
        val orderPayload = assetOrder?.map { item ->
            when (item) {
                is MediaEditOrderItem.Existing -> MediaAssetOrderItem(partId = item.partId)
                is MediaEditOrderItem.New -> {
                    val uploaded = uploadMediaPart(item.uri)
                    MediaAssetOrderItem(
                        type = uploaded.type,
                        assetId = uploaded.assetId,
                        thumbnailAssetId = uploaded.thumbnailAssetId,
                    )
                }
            }
        }
        call {
            api.updateMedia(
                id,
                UpdateMediaRequest(
                    caption = caption.trim(),
                    mediaDate = date,
                    assetOrder = orderPayload,
                ),
            )
        }
        refreshMedia()
    }

    sealed class MediaEditOrderItem {
        data class Existing(val partId: String) : MediaEditOrderItem()
        data class New(val uri: Uri) : MediaEditOrderItem()
    }

    suspend fun deleteMedia(id: String) {
        call { api.deleteMedia(id) }
        refreshMedia()
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

    private suspend fun uploadCoupleCover(uri: Uri): String {
        val source = withContext(Dispatchers.IO) { mediaResolver.resolve(uri) }
        require(!source.isVideo) { "情侣头像仅支持图片" }
        return uploadAsset(source, purpose = "cover")
    }

    private suspend fun uploadAsset(
        source: ResolvedMedia,
        purpose: String = "media",
        onBytes: ((Long, Long) -> Unit)? = null,
    ): String =
        uploadAsset(source.fileName, source.mimeType, source.sizeBytes, source.body, purpose, onBytes)

    private suspend fun uploadAsset(
        fileName: String,
        mimeType: String,
        sizeBytes: Long,
        body: RequestBody,
        purpose: String = "media",
        onBytes: ((Long, Long) -> Unit)? = null,
    ): String {
        val token = call {
            api.assetToken(TokenAssetRequest(fileName, mimeType, sizeBytes, purpose))
        }
        call { assetUploader.upload(token, fileName, body, onBytes) }
        call { api.completeAsset(AssetRequest(token.assetId)) }
        return token.assetId
    }

    suspend fun fetchUnreadPartnerActivity(): List<PartnerActivityEvent> {
        if (!tokenStore.snapshot.linked) return emptyList()
        return runCatching {
            call { api.partnerActivity(unreadOnly = true, limit = 20) }.items
        }.getOrDefault(emptyList())
    }

    suspend fun markPartnerActivityRead(ids: List<String>? = null, all: Boolean = false) {
        if (ids.isNullOrEmpty() && !all) return
        runCatching {
            call {
                api.markActivityRead(
                    MarkActivityReadRequest(
                        ids = ids?.takeIf { it.isNotEmpty() },
                        all = all.takeIf { it },
                    ),
                )
            }
        }
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
