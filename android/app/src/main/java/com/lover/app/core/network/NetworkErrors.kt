package com.lover.app.core.network

import com.lover.app.core.model.ApiErrorEnvelope
import java.io.IOException
import kotlinx.serialization.json.Json
import retrofit2.HttpException

class BackendException(
    val code: String,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

fun Throwable.toUserFacing(json: Json): BackendException = when (this) {
    is BackendException -> this
    is HttpException -> {
        val parsed = response()?.errorBody()?.string()
            ?.let { body -> runCatching { json.decodeFromString<ApiErrorEnvelope>(body) }.getOrNull() }
        BackendException(
            code = parsed?.error?.code ?: "HTTP_${code()}",
            message = parsed?.error?.message ?: "服务器请求失败（${code()}）",
            cause = this,
        )
    }
    is IOException -> BackendException("NETWORK_ERROR", "无法连接服务器，请检查网络与后端地址", this)
    else -> BackendException("CLIENT_ERROR", message ?: "操作失败", this)
}
