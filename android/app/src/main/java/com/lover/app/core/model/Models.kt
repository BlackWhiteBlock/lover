package com.lover.app.core.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String = "",
    val phone: String = "",
    val nickname: String = "我",
    val avatarUrl: String? = null,
)

@Serializable
data class CoupleSpace(
    val id: String = "",
    val inviteCode: String = "",
    val partnerNickname: String = "TA",
    val togetherDate: String = "",
    val establishedAt: String = "",
)

@Serializable
enum class MediaType { IMAGE, VIDEO }

@Serializable
data class MediaItem(
    val id: String,
    val type: MediaType = MediaType.IMAGE,
    val url: String,
    val thumbnailUrl: String? = null,
    val caption: String = "",
    val mediaDate: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
enum class AnniversaryType { MILESTONE, YEARLY }

@Serializable
data class Anniversary(
    val id: String,
    val title: String,
    val date: String,
    val type: AnniversaryType,
    val coverUrl: String? = null,
)

@Serializable
enum class LetterType { INSTANT, CAPSULE }

@Serializable
data class Letter(
    val id: String,
    val sender: String,
    val title: String,
    val content: String,
    val type: LetterType,
    val unlockDate: String? = null,
    val createdDate: String,
)

@Serializable
data class LocalState(
    val token: String? = null,
    val user: User? = null,
    val couple: CoupleSpace? = null,
    val media: List<MediaItem> = emptyList(),
    val anniversaries: List<Anniversary> = emptyList(),
    val letters: List<Letter> = emptyList(),
)

@Serializable data class SendCodeRequest(val phone: String)
@Serializable data class LoginRequest(val phone: String, val code: String)
@Serializable data class AuthResponse(val token: String, val user: User)
@Serializable data class CreateInviteRequest(val togetherDate: String)
@Serializable data class BindInviteRequest(val code: String, val togetherDate: String? = null)
@Serializable data class CoupleResponse(val couple: CoupleSpace)
@Serializable data class CreateMediaRequest(
    val type: MediaType,
    val url: String,
    val caption: String,
    val mediaDate: String,
)
@Serializable data class CreateAnniversaryRequest(
    val title: String,
    val date: String,
    val type: AnniversaryType,
)
@Serializable data class CreateLetterRequest(
    val title: String,
    val content: String,
    val type: LetterType,
    val unlockDate: String? = null,
)
