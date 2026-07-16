package com.lover.app.core.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.lover.app.BuildConfig
import com.lover.app.core.data.TokenStore
import com.lover.app.core.network.ApiHostAuthInterceptor
import com.lover.app.core.network.ApiService
import com.lover.app.core.network.OkHttpClients
import com.lover.app.core.network.RefreshApi
import com.lover.app.core.network.TokenAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun refreshApi(json: Json): RefreshApi =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(OkHttpClients.builder().build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RefreshApi::class.java)

    @Provides
    @Singleton
    @Named("asset-upload")
    fun assetUploadClient(): OkHttpClient =
        OkHttpClients.builder()
            // 视频直传体积大，默认 30s 写超时会误报失败
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.MINUTES)
            .writeTimeout(15, java.util.concurrent.TimeUnit.MINUTES)
            .callTimeout(20, java.util.concurrent.TimeUnit.MINUTES)
            .build()

    @Provides
    @Singleton
    fun apiService(
        json: Json,
        tokenStore: TokenStore,
        tokenAuthenticator: TokenAuthenticator,
    ): ApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        val authInterceptor = ApiHostAuthInterceptor(
            BuildConfig.API_BASE_URL.toHttpUrl(),
        ) { tokenStore.snapshot.accessToken }
        val client = OkHttpClients.builder()
            .addNetworkInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator)
            .addInterceptor(logging)
            .build()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }
}
