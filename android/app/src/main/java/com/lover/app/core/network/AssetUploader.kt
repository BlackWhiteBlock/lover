package com.lover.app.core.network

import com.lover.app.core.media.ProgressRequestBody
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
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody

@Singleton
class AssetUploader @Inject constructor(
    @Named("asset-upload") private val client: OkHttpClient,
    private val json: Json,
) {
    suspend fun upload(
        grant: TokenAssetResponse,
        fileName: String,
        fileBody: RequestBody,
        onBytes: ((bytesWritten: Long, contentLength: Long) -> Unit)? = null,
    ) = withContext(Dispatchers.IO) {
        val body = if (onBytes != null) {
            ProgressRequestBody(fileBody, onBytes)
        } else {
            fileBody
        }
        val request = buildRequest(grant, fileName, body)
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val responseBody = response.body?.string().orEmpty()
                    throw BackendException(
                        "UPLOAD_FAILED",
                        uploadError(response.code, responseBody),
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
        fileBody: RequestBody,
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
            fileBody,
        )
        return request.post(multipart.build()).build()
    }

    private fun uploadError(code: Int, body: String): String {
        if (code == 413) {
            return "上传被拒绝（413）：文件过大。若走服务器中转请把 Nginx client_max_body_size 调到 300m；也可压缩后再试"
        }
        val parsed = runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            root["error"]?.let { error ->
                runCatching { error.jsonObject["message"]?.jsonPrimitive?.content }.getOrNull()
                    ?: runCatching { error.jsonPrimitive.content }.getOrNull()
            } ?: root["message"]?.jsonPrimitive?.content
        }.getOrNull()?.takeIf { it.isNotBlank() }
        return when {
            parsed == null -> "媒体上传失败（$code）"
            parsed.contains("size", ignoreCase = true) ||
                parsed.contains("fsize", ignoreCase = true) ->
                "视频大小校验失败，请重试或换一段较短视频"
            parsed.contains("mime", ignoreCase = true) ->
                "不支持该视频格式，请转换为常见 MP4 后再试"
            else -> parsed
        }
    }
}
