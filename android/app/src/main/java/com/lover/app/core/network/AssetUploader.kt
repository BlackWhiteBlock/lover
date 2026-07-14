package com.lover.app.core.network

import com.lover.app.core.model.TokenAssetResponse
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class AssetUploader @Inject constructor(
    @Named("asset-upload") private val client: OkHttpClient,
    private val json: Json,
) {
    suspend fun upload(
        grant: TokenAssetResponse,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ) = withContext(Dispatchers.IO) {
        val request = buildRequest(grant, fileName, mimeType, bytes)
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    throw BackendException(
                        "UPLOAD_FAILED",
                        uploadError(body) ?: "媒体上传失败（${response.code}）",
                    )
                }
            }
        } catch (error: BackendException) {
            throw error
        } catch (error: IOException) {
            throw BackendException("UPLOAD_NETWORK_ERROR", "无法连接媒体存储服务", error)
        }
    }

    fun buildRequest(
        grant: TokenAssetResponse,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): Request {
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
        val request = Request.Builder().url(grant.uploadUrl)
        when (grant.provider) {
            "local" -> request.header("Authorization", "Bearer ${grant.uploadToken}")
            "qiniu" -> {
                multipart.addFormDataPart("token", grant.uploadToken)
                multipart.addFormDataPart("key", grant.uploadFields["key"] ?: grant.objectKey)
                grant.uploadFields.toSortedMap().forEach { (name, value) ->
                    if (name != "token" && name != "key" && name != "file") {
                        multipart.addFormDataPart(name, value)
                    }
                }
            }
            else -> throw BackendException("UNSUPPORTED_STORAGE_PROVIDER", "不支持的存储服务：${grant.provider}")
        }
        multipart.addFormDataPart(
            "file",
            fileName,
            bytes.toRequestBody(mimeType.toMediaType()),
        )
        return request.post(multipart.build()).build()
    }

    private fun uploadError(body: String): String? = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        root["error"]?.let { error ->
            runCatching { error.jsonObject["message"]?.jsonPrimitive?.content }.getOrNull()
                ?: runCatching { error.jsonPrimitive.content }.getOrNull()
        }
    }.getOrNull()
}
