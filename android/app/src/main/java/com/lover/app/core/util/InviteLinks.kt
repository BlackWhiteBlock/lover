package com.lover.app.core.util

import android.net.Uri
import com.lover.app.BuildConfig

object InviteLinks {
    fun buildUrl(code: String): String {
        val base = BuildConfig.INVITE_BASE_URL.trimEnd('/')
        return "$base/invite/${code.trim().uppercase()}"
    }

    fun parseCode(uri: Uri?): String? {
        if (uri == null) return null
        val hostPathCode = when {
            uri.scheme.equals("lover", ignoreCase = true) &&
                uri.host.equals("invite", ignoreCase = true) -> {
                uri.pathSegments.firstOrNull()
            }
            uri.pathSegments.size >= 2 &&
                uri.pathSegments[0].equals("invite", ignoreCase = true) -> {
                uri.pathSegments[1]
            }
            uri.pathSegments.size == 1 &&
                uri.host.equals("invite", ignoreCase = true) -> {
                uri.pathSegments[0]
            }
            else -> null
        }
        val code = hostPathCode?.trim()?.uppercase().orEmpty()
        return code.takeIf { it.length in 6..32 }
    }

    fun shareText(code: String, inviteUrl: String = buildUrl(code)): String =
        "来 Lover 和我组成小宇宙～\n打开链接或输入邀请码：$code\n$inviteUrl"
}
