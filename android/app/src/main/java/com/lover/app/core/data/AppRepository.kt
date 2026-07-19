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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    /** 登录后后台补齐时光/纪念/信封，不阻塞 sessionReady */
    private val warmScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun sendSms(phone: String): SendSmsResponse =
        call { api.sendSms(SendSmsRequest(phone.trim())) }

    suspend fun login(phone: String, code: String) {
        val auth = call { api.login(LoginRequest(phone.trim(), code.trim())) }
        applyAuthSession(auth)
    }

    suspend fun fetchPnvsSdkInfo(): PnvsSdkInfoResponse =
        call { api.pnvsSdkInfo("android") }

    suspend fun loginWithPnvsToken(loginToken: String) {
        val auth = call { api.pnvsLogin(PnvsLoginRequest(loginToken = loginToken)) }
        applyAuthSession(auth)
    }

    private suspend fun applyAuthSession(auth: AuthResponse) {
        tokenStore.saveTokens(auth.accessToken, auth.refreshToken)
        try {
            applyMe(call { api.me() }, clearContent = true)
            if (tokenStore.snapshot.profileCompleted) {
                refreshHomeEssentials(fetchMe = false)
                warmSecondaryHomeData()
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
                refreshHomeEssentials(fetchMe = false)
                warmSecondaryHomeData()
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

    /** 仅更新本账号个人头像（绑定 / 未绑定均可） */
    suspend fun updateAvatar(avatarUri: Uri) {
        val assetId = uploadAvatar(avatarUri)
        val user = call { api.patchMe(PatchMeRequest(avatarAssetId = assetId)) }.user
        tokenStore.update { it.copy(user = user) }
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

    /**
     * 完整刷新：先解锁「空间」首屏，再等待时光/纪念/信封（下拉刷新等）。
     * 登录请用 [refreshHomeEssentials] + [warmSecondaryHomeData]。
     */
    suspend fun refreshAll() {
        refreshHomeEssentials(fetchMe = true)
        awaitSecondaryHomeData()
    }

    /**
     * 「空间」首页必需数据。掠影用 bootstrap.recentMedia（8）签列表缩略图，
     * 不再请求 media?limit=100 并对全部 asset 签原图。
     */
    suspend fun refreshHomeEssentials(fetchMe: Boolean = true) = coroutineScope {
        val meDeferred = if (fetchMe) {
            async { runCatching { call { api.me() } }.getOrNull() }
        } else {
            async { null }
        }
        val bootstrapDeferred = async { runCatching { call { api.bootstrap() } }.getOrNull() }
        val coupleDeferred = async { runCatching { call { api.coupleSpace() } }.getOrNull() }
        val pendingDeferred = async { runCatching { call { api.pendingBinds() } }.getOrNull() }
        val previous = tokenStore.snapshot

        val me = meDeferred.await()
        val bootstrap = bootstrapDeferred.await()
        val coupleBase = coupleDeferred.await()
        val pending = pendingDeferred.await()

        if (me == null && bootstrap == null && coupleBase == null && pending == null) {
            error("刷新失败，请检查网络后重试")
        }

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

        val media = if (bootstrap != null && bootstrap.recentMedia.isNotEmpty()) {
            runCatching {
                signMediaCovers(sortMediaByRecordDate(bootstrap.recentMedia), previous.media)
            }.getOrDefault(previous.media)
        } else {
            previous.media
        }

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
                sessionReady = true,
            )
        }
    }

    private fun warmSecondaryHomeData() {
        warmScope.launch {
            runCatching { awaitSecondaryHomeData() }
        }
    }

    private suspend fun awaitSecondaryHomeData() = coroutineScope {
        val mediaJob = async { runCatching { refreshMedia() } }
        val yearsJob = async { runCatching { refreshMediaYears() } }
        val unreadJob = async { runCatching { refreshMediaUnreadSummary() } }
        val letterUnreadJob = async { runCatching { refreshLetterUnreadSummary() } }
        val annJob = async {
            runCatching { call { api.anniversaries().items } }.getOrNull()
        }
        val lettersJob = async {
            runCatching { call { api.letters().items } }.getOrNull()
        }
        mediaJob.await()
        yearsJob.await()
        unreadJob.await()
        letterUnreadJob.await()
        val anniversaries = annJob.await()
        val letters = lettersJob.await()
        if (anniversaries != null || letters != null) {
            tokenStore.update {
                it.copy(
                    anniversaries = anniversaries ?: it.anniversaries,
                    letters = letters ?: it.letters,
                )
            }
        }
    }

    private var mediaCursor: String? = null
    private var mediaYearFilter: Int? = null
    private var loadingMore = false
    private val _mediaHasMore = MutableStateFlow(false)
    val mediaHasMore = _mediaHasMore.asStateFlow()
    private val _mediaYears = MutableStateFlow<List<Int>>(emptyList())
    val mediaYears = _mediaYears.asStateFlow()
    private val _mediaUnreadCount = MutableStateFlow(0)
    val mediaUnreadCount = _mediaUnreadCount.asStateFlow()
    private val _mediaYearFilterFlow = MutableStateFlow<Int?>(null)
    val mediaYearFilterState = _mediaYearFilterFlow.asStateFlow()

    /** 仅刷新时光列表（首屏 30 条 + 封面签名），保留已有缩略图签名 */
    suspend fun refreshMedia(year: Int? = mediaYearFilter) {
        mediaYearFilter = year
        _mediaYearFilterFlow.value = year
        val previous = tokenStore.snapshot.media
        val page = call { api.media(limit = 30, year = year) }
        mediaCursor = page.nextCursor
        _mediaHasMore.value = page.nextCursor != null
        val signed = runCatching {
            signMediaCovers(sortMediaByRecordDate(page.items), previous)
        }.getOrDefault(previous)
        tokenStore.update { it.copy(media = signed) }
    }

    suspend fun setMediaYearFilter(year: Int?) {
        if (mediaYearFilter == year) return
        refreshMedia(year)
    }

    suspend fun refreshMediaYears() {
        val years = runCatching { call { api.mediaYears() }.years }.getOrDefault(emptyList())
        _mediaYears.value = years
    }

    suspend fun refreshMediaUnreadSummary() {
        if (!tokenStore.snapshot.linked) {
            _mediaUnreadCount.value = 0
            return
        }
        val summary = runCatching { call { api.mediaUnreadSummary() } }.getOrNull() ?: return
        _mediaUnreadCount.value = summary.count
    }

    private val _letterUnreadCount = MutableStateFlow(0)
    val letterUnreadCount = _letterUnreadCount.asStateFlow()

    suspend fun refreshLetterUnreadSummary() {
        if (!tokenStore.snapshot.linked) {
            _letterUnreadCount.value = 0
            return
        }
        val summary = runCatching { call { api.letterUnreadSummary() } }.getOrNull() ?: return
        _letterUnreadCount.value = summary.count
    }

    suspend fun openLetter(id: String): Letter {
        val opened = call { api.openLetter(id) }
        tokenStore.update { state ->
            state.copy(letters = state.letters.map { if (it.id == id) opened else it })
        }
        runCatching { refreshLetterUnreadSummary() }
        return opened
    }

    suspend fun fetchLetterDetail(id: String): Letter {
        val detail = call { api.letterDetail(id) }
        tokenStore.update { state ->
            val exists = state.letters.any { it.id == id }
            state.copy(
                letters = if (exists) {
                    state.letters.map { if (it.id == id) detail else it }
                } else {
                    listOf(detail) + state.letters
                },
            )
        }
        return detail
    }

    suspend fun fetchUnreadMedia(cursor: String? = null, limit: Int = 30): MediaUnreadPage {
        val page = call { api.mediaUnread(cursor = cursor, limit = limit) }
        val previous = tokenStore.snapshot.media
        val signed = runCatching {
            signMediaCovers(sortMediaByRecordDate(page.items), previous)
        }.getOrDefault(page.items)
        _mediaUnreadCount.value = page.count
        return page.copy(items = signed)
    }

    suspend fun markMediaRead(id: String) {
        val summary = runCatching { call { api.markMediaRead(id) } }.getOrNull() ?: return
        _mediaUnreadCount.value = summary.count
    }

    suspend fun markAllUnreadMediaRead() {
        val summary = runCatching {
            call { api.markMediaReadBatch(MarkMediaReadRequest(all = true)) }
        }.getOrNull() ?: return
        _mediaUnreadCount.value = summary.count
    }

    /** 加载更多媒体（cursor 分页）；返回是否成功加载了更多数据 */
    suspend fun loadMoreMedia(): Boolean {
        val cursor = mediaCursor ?: run {
            _mediaHasMore.value = false
            return false
        }
        if (loadingMore) return false
        loadingMore = true
        try {
            val page = call { api.media(cursor = cursor, limit = 30, year = mediaYearFilter) }
            mediaCursor = page.nextCursor
            _mediaHasMore.value = page.nextCursor != null
            if (page.items.isEmpty()) {
                _mediaHasMore.value = false
                return false
            }
            val previous = tokenStore.snapshot.media
            val signed = runCatching {
                signMediaCovers(page.items, previous)
            }.getOrDefault(page.items)
            val merged = previous + signed
            tokenStore.update { it.copy(media = sortMediaByRecordDate(merged)) }
            return true
        } finally {
            loadingMore = false
        }
    }

    private fun sortMediaByRecordDate(items: List<MediaItem>): List<MediaItem> =
        items.sortedWith(
            compareByDescending<MediaItem> { it.mediaDate }
                .thenByDescending { it.createdAt },
        )

    /**
     * 列表缩略图签名。
     * 时光页 Featured strip 最多展示 5 张，Duo/Strip 需要 2–3 张；
     * 只签封面会导致后续格空白。详情原图仍走 ensureMediaOriginals。
     */
    private suspend fun signMediaCovers(
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
                    signAssetsForList(item)
                }
            }
        }.awaitAll()
    }

    private suspend fun signAssetsForList(item: MediaItem): MediaItem = coroutineScope {
        if (item.assets.isEmpty()) return@coroutineScope item
        val toSign = item.assets
            .sortedBy { it.sortOrder }
            .take(LIST_SIGNED_ASSET_LIMIT)
        val signedParts = toSign.map { part -> async { signAssetForList(part) } }.awaitAll()
        val byPartId = signedParts.filter { it.id.isNotBlank() }.associateBy { it.id }
        val byAssetId = signedParts.associateBy { it.assetId }
        item.copy(
            assets = item.assets.map { part ->
                byPartId[part.id] ?: byAssetId[part.assetId] ?: part
            },
        )
    }

    private fun mediaAssetFingerprint(assets: List<MediaAssetPart>): List<String> =
        assets.sortedBy { it.sortOrder }.map { part ->
            "${part.id}:${part.assetId}:${part.type.name}:${part.sortOrder}"
        }

    private fun listVisibleAssets(assets: List<MediaAssetPart>): List<MediaAssetPart> =
        assets.sortedBy { it.sortOrder }.take(LIST_SIGNED_ASSET_LIMIT)

    private fun canReuseSignedThumbs(item: MediaItem, prev: MediaItem): Boolean {
        if (mediaAssetFingerprint(item.assets) != mediaAssetFingerprint(prev.assets)) return false
        val prevByPartId = prev.assets.filter { it.id.isNotBlank() }.associateBy { it.id }
        val prevByAssetId = prev.assets.associateBy { it.assetId }
        val visible = listVisibleAssets(item.assets)
        if (visible.isEmpty()) return false
        return visible.all { part ->
            val cached = prevByPartId[part.id] ?: prevByAssetId[part.assetId]
            cached != null && (
                isReusableSignedUrl(cached.thumbnailUrl) || isReusableSignedUrl(cached.url)
            )
        }
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

    suspend fun updateAnniversary(id: String, title: String, date: String, type: AnniversaryType) {
        call {
            api.updateAnniversary(
                id,
                UpdateAnniversaryRequest(title = title.trim(), date = date, type = type),
            )
        }
        refreshAll()
    }

    suspend fun deleteAnniversary(id: String) {
        call { api.deleteAnniversary(id) }
        refreshAll()
    }

    /** 签发单个 asset 的原始 URL（用于纪念日封面等非媒体列表场景） */
    suspend fun signAssetOriginalUrl(assetId: String): String =
        call { api.signAsset(assetId, SignAssetRequest("original")).url }

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

    suspend fun deleteLetter(id: String) {
        call { api.deleteLetter(id) }
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
        /** Timeline Featured 最多 5 格；Duo/Strip 需 2–3 张。超出部分详情再签。 */
        private const val LIST_SIGNED_ASSET_LIMIT = 5

        fun localMidnightWithOffset(date: String, zoneId: ZoneId = ZoneId.systemDefault()): String =
            LocalDate.parse(date)
                .atStartOfDay(zoneId)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }
}
