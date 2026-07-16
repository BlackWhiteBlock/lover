package com.lover.app.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class User(
    val id: String = "",
    val phone: String = "",
    val nickname: String = "我",
    val avatarUrl: String? = null,
    /** 个人偏好的情侣合照（各账号独立，不与伴侣同步） */
    val coupleCoverUrl: String? = null,
    val gender: String? = null,
    val birthday: String? = null,
    val profileCompleted: Boolean = false,
    val personalSpaceId: String? = null,
)

@Serializable
data class CoupleSpace(
    val id: String = "",
    val name: String = "我们的小宇宙",
    val togetherDate: String? = null,
    val status: String = "active",
    val createdAt: String = "",
    val kind: String? = null,
    val linked: Boolean = false,
    val coupleLinkId: String? = null,
    val personalSpaceId: String? = null,
    val loverSpaceId: String? = null,
    val members: List<CoupleMember> = emptyList(),
    val pendingUnbinding: UnbindingRequest? = null,
    val pendingIncomingBinds: List<IncomingBindRequest> = emptyList(),
    val pendingOutgoingBind: OutgoingBindRequest? = null,
)

@Serializable
data class IncomingBindRequest(
    val id: String = "",
    val requesterId: String = "",
    val requesterNickname: String = "",
    val requesterPhone: String = "",
    val requesterAvatarUrl: String? = null,
    val status: String = "pending",
    val expiresAt: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class OutgoingBindRequest(
    val id: String = "",
    val targetUserId: String = "",
    val targetNickname: String = "",
    val targetPhone: String = "",
    val targetAvatarUrl: String? = null,
    val status: String = "pending",
    val expiresAt: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class PendingBindsResponse(
    val incoming: List<IncomingBindRequest> = emptyList(),
    val outgoing: List<OutgoingBindRequest> = emptyList(),
)

@Serializable
data class CoupleMember(
    val id: String,
    val nickname: String,
    val avatarUrl: String? = null,
    val joinedAt: String? = null,
)

@Serializable
enum class MediaType {
    @SerialName("image") IMAGE,
    @SerialName("video") VIDEO,
}

@Serializable
data class MediaAssetPart(
    val id: String = "",
    val type: MediaType = MediaType.IMAGE,
    val assetId: String,
    val thumbnailAssetId: String? = null,
    val sortOrder: Int = 0,
    val url: String = "",
    val thumbnailUrl: String? = null,
) {
    val previewUrl: String get() = thumbnailUrl?.takeIf { it.isNotBlank() } ?: url
}

@Serializable
data class MediaItem(
    val id: String,
    val caption: String = "",
    val mediaDate: String,
    val uploaderId: String? = null,
    val createdAt: String = "",
    val assets: List<MediaAssetPart> = emptyList(),
) {
    val cover: MediaAssetPart? get() = assets.minByOrNull { it.sortOrder } ?: assets.firstOrNull()
    val type: MediaType get() = cover?.type ?: MediaType.IMAGE
    val url: String get() = cover?.url.orEmpty()
    /** 列表/掠影优先缩略图；无缩略图时才退回原图 */
    val thumbnailUrl: String? get() = cover?.thumbnailUrl?.takeIf { it.isNotBlank() }
        ?: cover?.url?.takeIf { it.isNotBlank() }
    val assetCount: Int get() = assets.size
}

@Serializable
enum class AnniversaryType {
    @SerialName("milestone") MILESTONE,
    @SerialName("yearly") YEARLY,
}

@Serializable
data class Anniversary(
    val id: String,
    val title: String,
    val date: String,
    val type: AnniversaryType,
    val coverAssetId: String? = null,
    val createdBy: String? = null,
    val createdAt: String = "",
    val countdown: Countdown? = null,
)

@Serializable
data class Countdown(val days: Int, val nextDate: String? = null, val reached: Boolean)

@Serializable
enum class LetterType {
    @SerialName("instant") INSTANT,
    @SerialName("capsule") CAPSULE,
}

@Serializable
data class Letter(
    val id: String,
    val senderId: String,
    val senderNickname: String,
    val title: String,
    val content: String? = null,
    val summary: String? = null,
    val type: LetterType,
    val unlockAt: String? = null,
    val unlockOnPartnerBind: Boolean = false,
    val isUnlocked: Boolean,
    val createdAt: String,
)

@Serializable
data class AppState(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val user: User? = null,
    val activeSpaceId: String? = null,
    val personalSpaceId: String? = null,
    val loverSpaceId: String? = null,
    val profileCompleted: Boolean = false,
    val linked: Boolean = false,
    val couple: CoupleSpace? = null,
    /** 与 CoupleSpace 同步；单独存放避免嵌套反序列化丢失 */
    val pendingIncomingBinds: List<IncomingBindRequest> = emptyList(),
    val pendingOutgoingBind: OutgoingBindRequest? = null,
    val lovingDays: Int? = null,
    val needsTogetherDate: Boolean = false,
    val media: List<MediaItem> = emptyList(),
    val anniversaries: List<Anniversary> = emptyList(),
    val letters: List<Letter> = emptyList(),
    val loading: Boolean = false,
    /**
     * 登录/恢复会话完成且绑定数据已写入后才为 true。
     * 避免 saveTokens 后立刻进主界面、绑定状态还是空的。
     */
    val sessionReady: Boolean = false,
    @Transient val sessionLoaded: Boolean = false,
)

@Serializable data class SendSmsRequest(val phone: String)
@Serializable data class SendSmsResponse(
    val ok: Boolean,
    val cooldownSeconds: Int,
    val devCode: String? = null,
)
@Serializable data class LoginRequest(val phone: String, val code: String, val nickname: String? = null)
@Serializable data class AuthResponse(
    val user: User,
    val isNewUser: Boolean = false,
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresIn: String,
)
@Serializable data class RefreshRequest(val refreshToken: String)
@Serializable data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresIn: String,
)
@Serializable data class OkResponse(val ok: Boolean)
@Serializable data class MeResponse(
    val user: User,
    val personalSpaceId: String? = null,
    val loverSpaceId: String? = null,
    val activeSpaceId: String? = null,
    val profileCompleted: Boolean = false,
    val linked: Boolean = false,
)
@Serializable data class OnboardingRequest(
    val nickname: String,
    val avatarUrl: String? = null,
    val gender: String,
    val birthday: String,
    val spaceName: String,
)
@Serializable data class OnboardingResponse(
    val user: User,
    val personalSpaceId: String,
    val spaceName: String,
)
@Serializable data class CreateBindRequest(val phone: String)
@Serializable data class BindRequestDto(
    val id: String,
    val requesterId: String? = null,
    val targetUserId: String? = null,
    val targetNickname: String? = null,
    val targetPhone: String? = null,
    val targetAvatarUrl: String? = null,
    val status: String = "pending",
    val expiresAt: String? = null,
    val createdAt: String? = null,
)
@Serializable data class AcceptBindResponse(
    val coupleLinkId: String,
    val loverSpaceId: String,
    val togetherDate: String? = null,
    val needsTogetherDate: Boolean = true,
)
@Serializable data class UpdateCoupleLinkRequest(
    val togetherDate: String? = null,
    val name: String? = null,
)
@Serializable data class CoupleLinkResponse(
    val id: String? = null,
    val loverSpaceId: String? = null,
    val togetherDate: String? = null,
    val name: String? = null,
)
@Serializable data class UpdateCoupleSpaceRequest(val name: String? = null, val togetherDate: String? = null)
@Serializable data class BootstrapResponse(
    val space: CoupleSpace,
    val lovingJourney: LovingJourney,
    val recentMedia: List<MediaItem>,
    val linked: Boolean = false,
    val coupleLinkId: String? = null,
)
@Serializable data class LovingJourney(
    val togetherDate: String? = null,
    val days: Int? = null,
    val needsTogetherDate: Boolean,
)
@Serializable data class ItemPage<T>(val items: List<T>, val nextCursor: String? = null)
@Serializable data class TokenAssetRequest(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val purpose: String = "media",
)
@Serializable data class PatchMeRequest(
    val avatarAssetId: String? = null,
    val coupleCoverAssetId: String? = null,
    val clearCoupleCover: Boolean = false,
)
@Serializable data class PatchMeResponse(val user: User)
@Serializable data class TokenAssetResponse(
    val assetId: String,
    val provider: String,
    val uploadToken: String,
    val uploadUrl: String,
    val objectKey: String,
    val uploadFields: Map<String, String> = emptyMap(),
    val expiresIn: Int,
)
@Serializable data class UploadResponse(val ok: Boolean, val assetId: String, val sizeBytes: Long)
@Serializable data class AssetRequest(val assetId: String)
@Serializable data class CompleteAssetResponse(val assetId: String, val status: String, val sizeBytes: Long)
@Serializable data class SignAssetRequest(val variant: String = "original")
@Serializable data class SignAssetResponse(val url: String, val expiresIn: Int)
@Serializable data class CreateMediaAssetRequest(
    val type: MediaType,
    val assetId: String,
    val thumbnailAssetId: String? = null,
)
@Serializable data class CreateMediaRequest(
    val caption: String,
    val mediaDate: String,
    val assets: List<CreateMediaAssetRequest>,
)
@Serializable data class UpdateMediaRequest(
    val caption: String? = null,
    val mediaDate: String? = null,
    val addAssets: List<CreateMediaAssetRequest>? = null,
    val removeAssetPartIds: List<String>? = null,
)
@Serializable data class CreateAnniversaryRequest(
    val title: String,
    val date: String,
    val type: AnniversaryType,
    val coverAssetId: String? = null,
)
@Serializable data class UpdateAnniversaryRequest(
    val title: String? = null,
    val date: String? = null,
    val type: AnniversaryType? = null,
    val coverAssetId: String? = null,
)
@Serializable data class CreateLetterRequest(
    val title: String,
    val content: String,
    val type: LetterType,
    val unlockAt: String? = null,
    val unlockOnPartnerBind: Boolean = false,
)
@Serializable data class UnbindingRequest(
    val id: String,
    val requestedBy: String? = null,
    val status: String,
    val reason: String? = null,
    val createdAt: String,
)
@Serializable data class CreateUnbindingRequest(val reason: String? = null)
@Serializable data class ApiErrorEnvelope(val error: ApiErrorBody)
@Serializable data class ApiErrorBody(val code: String, val message: String)
