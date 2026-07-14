package com.lover.app.core.network

import com.lover.app.core.model.*
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Lover backend contract. All routes intentionally live here so a backend can
 * align without searching through UI code. Bearer authentication is injected.
 */
interface ApiService {
    @POST("api/auth/code")
    suspend fun sendCode(@Body request: SendCodeRequest)

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("api/auth/logout")
    suspend fun logout()

    @GET("api/couple")
    suspend fun couple(): CoupleResponse

    @POST("api/couple/invites")
    suspend fun createInvite(@Body request: CreateInviteRequest): CoupleResponse

    @POST("api/couple/bind")
    suspend fun bindInvite(@Body request: BindInviteRequest): CoupleResponse

    @DELETE("api/couple/bind")
    suspend fun unbind()

    @GET("api/media")
    suspend fun media(): List<MediaItem>

    @POST("api/media")
    suspend fun createMedia(@Body request: CreateMediaRequest): MediaItem

    @GET("api/anniversaries")
    suspend fun anniversaries(): List<Anniversary>

    @POST("api/anniversaries")
    suspend fun createAnniversary(@Body request: CreateAnniversaryRequest): Anniversary

    @GET("api/letters")
    suspend fun letters(): List<Letter>

    @POST("api/letters")
    suspend fun createLetter(@Body request: CreateLetterRequest): Letter
}
