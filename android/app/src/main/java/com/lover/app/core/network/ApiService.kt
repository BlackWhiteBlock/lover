package com.lover.app.core.network

import com.lover.app.core.model.*
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Lover backend contract. All routes intentionally live here so a backend can
 * align without searching through UI code. Bearer authentication is injected.
 */
interface ApiService {
    @POST("api/auth/sms/send")
    suspend fun sendSms(@Body request: SendSmsRequest): SendSmsResponse

    @POST("api/auth/sms/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("api/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): TokenResponse

    @POST("api/auth/logout")
    suspend fun logout(): OkResponse

    @GET("api/me")
    suspend fun me(): MeResponse

    @GET("api/couple-space")
    suspend fun coupleSpace(): CoupleSpace

    @PATCH("api/couple-space")
    suspend fun updateCoupleSpace(@Body request: UpdateCoupleSpaceRequest): CoupleSpace

    @POST("api/onboarding")
    suspend fun onboarding(@Body request: OnboardingRequest): OnboardingResponse

    @POST("api/couple-binds")
    suspend fun createBind(@Body request: CreateBindRequest): BindRequestDto

    @POST("api/couple-binds/{id}/accept")
    suspend fun acceptBind(@Path("id") id: String): AcceptBindResponse

    @POST("api/couple-binds/{id}/reject")
    suspend fun rejectBind(@Path("id") id: String): OkResponse

    @POST("api/couple-binds/{id}/cancel")
    suspend fun cancelBind(@Path("id") id: String): OkResponse

    @PATCH("api/couple-link")
    suspend fun updateCoupleLink(@Body request: UpdateCoupleLinkRequest): CoupleLinkResponse

    @GET("api/bootstrap")
    suspend fun bootstrap(): BootstrapResponse

    @POST("api/media-assets/token")
    suspend fun assetToken(@Body request: TokenAssetRequest): TokenAssetResponse

    @POST("api/media-assets/complete")
    suspend fun completeAsset(@Body request: AssetRequest): CompleteAssetResponse

    @POST("api/media-assets/{assetId}/sign")
    suspend fun signAsset(@Path("assetId") assetId: String): SignAssetResponse

    @GET("api/media")
    suspend fun media(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 100,
    ): ItemPage<MediaItem>

    @POST("api/media")
    suspend fun createMedia(@Body request: CreateMediaRequest): MediaItem

    @GET("api/media/{id}")
    suspend fun mediaDetail(@Path("id") id: String): MediaItem

    @PATCH("api/media/{id}")
    suspend fun updateMedia(@Path("id") id: String, @Body request: UpdateMediaRequest): MediaItem

    @DELETE("api/media/{id}")
    suspend fun deleteMedia(@Path("id") id: String): OkResponse

    @POST("api/anniversaries")
    suspend fun createAnniversary(@Body request: CreateAnniversaryRequest): Anniversary

    @GET("api/anniversaries")
    suspend fun anniversaries(): ItemPage<Anniversary>

    @GET("api/anniversaries/{id}")
    suspend fun anniversaryDetail(@Path("id") id: String): Anniversary

    @PATCH("api/anniversaries/{id}")
    suspend fun updateAnniversary(
        @Path("id") id: String,
        @Body request: UpdateAnniversaryRequest,
    ): Anniversary

    @DELETE("api/anniversaries/{id}")
    suspend fun deleteAnniversary(@Path("id") id: String): OkResponse

    @POST("api/letters")
    suspend fun createLetter(@Body request: CreateLetterRequest): Letter

    @GET("api/letters")
    suspend fun letters(): ItemPage<Letter>

    @GET("api/letters/{id}")
    suspend fun letterDetail(@Path("id") id: String): Letter

    @retrofit2.http.PUT("api/letters/{id}")
    suspend fun updateLetter(@Path("id") id: String, @Body request: CreateLetterRequest): Letter

    @DELETE("api/letters/{id}")
    suspend fun deleteLetter(@Path("id") id: String): OkResponse

    @POST("api/couple-space/unbinding")
    suspend fun requestUnbinding(@Body request: CreateUnbindingRequest): UnbindingRequest

    @POST("api/couple-space/unbinding/{id}/confirm")
    suspend fun confirmUnbinding(@Path("id") id: String): OkResponse

    @POST("api/couple-space/unbinding/{id}/cancel")
    suspend fun cancelUnbinding(@Path("id") id: String): OkResponse
}

interface RefreshApi {
    @POST("api/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): TokenResponse
}
