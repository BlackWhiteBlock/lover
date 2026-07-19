package com.lover.app.core.auth

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.reflect.Proxy
import java.security.MessageDigest
import org.json.JSONObject

/**
 * Reflection bridge for Aliyun PNVS one-click login.
 * Compiles without local aars; at runtime requires auth/logger/main aars in app/libs.
 */
class PnvsLoginHelper(
    private val appContext: Context,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var helper: Any? = null
    private var lastSecretPreview: String = ""
    private var lastPrivacyUrl: String = ""
    private var lastTermsUrl: String = ""

    fun isSdkAvailable(): Boolean = runCatching {
        Class.forName(HELPER)
        true
    }.getOrDefault(false)

    fun debugSnapshot(): Map<String, String> = mapOf(
        "package" to appContext.packageName,
        "signMd5" to currentSigningMd5(),
        "sdkAvailable" to isSdkAvailable().toString(),
        "helperReady" to (helper != null).toString(),
        "secretPreview" to lastSecretPreview.ifBlank { "(empty)" },
        "privacyUrl" to lastPrivacyUrl.ifBlank { "(empty)" },
        "termsUrl" to lastTermsUrl.ifBlank { "(empty)" },
    )

    fun prepare(
        secretInfo: String,
        privacyUrl: String,
        termsUrl: String,
        onLog: (String) -> Unit = {},
        onEnvReady: (Boolean) -> Unit,
    ) {
        if (!isSdkAvailable() || secretInfo.isBlank()) {
            onLog("prepare: sdk missing or secret blank")
            onEnvReady(false)
            return
        }
        lastSecretPreview = secretPreview(secretInfo)
        lastPrivacyUrl = privacyUrl
        lastTermsUrl = termsUrl
        onLog("prepare: secret=$lastSecretPreview privacy=$privacyUrl")

        runCatching {
            var settled = false
            fun settle(ready: Boolean, reason: String) {
                if (settled) return
                settled = true
                onLog("prepare: settle ready=$ready ($reason)")
                if (!ready) helper = null
                mainHandler.post { onEnvReady(ready) }
            }

            val envOk = resultCodeConst("CODE_ERROR_ENV_CHECK_SUCCESS")
            val envFail = resultCodeConst("CODE_ERROR_ENV_CHECK_FAIL")
            val analyzeFail = resultCodeConst("CODE_ERROR_ANALYZE_SDK_INFO")
            val listener = tokenListener(onLog) { payload ->
                val code = payloadCode(payload)
                val msg = payload.optString("msg", payload.optString("message", ""))
                onLog("prepare.cb code=$code msg=$msg")
                when (code) {
                    envOk -> settle(true, "envCheck ok")
                    envFail -> settle(false, "envCheck fail: $msg")
                    analyzeFail -> settle(false, "密钥/包名/签名不匹配: $msg")
                    else -> Unit // wait for env callback or optimistic timeout
                }
            }
            val getInstance = Class.forName(HELPER).getMethod(
                "getInstance",
                Context::class.java,
                Class.forName(TOKEN_LISTENER),
            )
            helper = getInstance.invoke(null, appContext.applicationContext, listener)
            enableSdkLogger()
            helper!!.javaClass.getMethod("setAuthSDKInfo", String::class.java)
                .invoke(helper, secretInfo)
            applyUiConfig(privacyUrl, termsUrl)
            onLog("prepare: uiConfig applied")
            val serviceType = Class.forName(HELPER).getField("SERVICE_TYPE_LOGIN").getInt(null)
            helper!!.javaClass.getMethod("checkEnvAvailable", Int::class.javaPrimitiveType)
                .invoke(helper, serviceType)
            runCatching {
                val preLogin = Class.forName(PRE_LOGIN_LISTENER)
                val accelerateListener = Proxy.newProxyInstance(
                    preLogin.classLoader,
                    arrayOf(preLogin),
                ) { _, method, args ->
                    when (method.name) {
                        "onTokenSuccess" ->
                            onLog("accelerate ok vendor=${args?.getOrNull(0)}")
                        "onTokenFailed" ->
                            onLog("accelerate fail vendor=${args?.getOrNull(0)} err=${args?.getOrNull(1)}")
                    }
                    null
                }
                helper!!.javaClass.getMethod(
                    "accelerateLoginPage",
                    Int::class.javaPrimitiveType,
                    preLogin,
                ).invoke(helper, 5_000, accelerateListener)
            }.onFailure {
                onLog("accelerate skipped: ${it.message}")
            }
            // If env-check callback never arrives, fall back to optimistic ready after short wait.
            mainHandler.postDelayed({
                if (!settled && helper != null) {
                    settle(true, "envCheck timeout → optimistic ready")
                }
            }, 2_500)
        }.onFailure {
            Log.e(TAG, "prepare failed", it)
            onLog("prepare exception: ${it.javaClass.simpleName}: ${it.message}")
            helper = null
            mainHandler.post { onEnvReady(false) }
        }
    }

    fun getLoginToken(
        activity: Activity,
        timeoutMs: Int = 8_000,
        onLog: (String) -> Unit = {},
        onResult: (Result<String>) -> Unit,
    ) {
        val h = helper
        if (h == null) {
            onLog("getLoginToken: helper null")
            onResult(Result.failure(IllegalStateException("本机号登录不可用，请使用短信验证码")))
            return
        }
        onLog("getLoginToken: timeout=${timeoutMs}ms activity=${activity.javaClass.simpleName}")
        runCatching {
            val listener = tokenListener(onLog) { payload ->
                val code = payloadCode(payload)
                val msg = payload.optString("msg", payload.optString("message", "本机号登录失败"))
                val vendor = payload.optString("vendor", payload.optString("carrier", ""))
                onLog("token.cb code=$code vendor=$vendor msg=$msg raw=${payload.toString().take(240)}")
                when (code) {
                    "600000" -> {
                        val token = payload.optString("token", payload.optString("_token", ""))
                        if (token.isNotBlank()) {
                            quitLoginPage()
                            onResult(Result.success(token))
                        } else {
                            onResult(Result.failure(IllegalStateException("取号失败，请使用短信验证码")))
                        }
                    }
                    "600001" -> Unit // auth page shown
                    "700000", "700001" ->
                        onResult(Result.failure(IllegalStateException("已取消本机号登录")))
                    else -> {
                        if (code.isNotBlank() && !code.startsWith("700")) {
                            quitLoginPage()
                            val detail = buildString {
                                append(msg.ifBlank { "本机号登录失败，请使用短信验证码" })
                                if (code.isNotBlank()) append(" [$code]")
                                if (vendor.isNotBlank()) append(" ($vendor)")
                            }
                            onResult(Result.failure(IllegalStateException(detail)))
                        }
                    }
                }
            }
            h.javaClass.getMethod("setAuthListener", Class.forName(TOKEN_LISTENER))
                .invoke(h, listener)
            h.javaClass.getMethod(
                "getLoginToken",
                Context::class.java,
                Int::class.javaPrimitiveType,
            ).invoke(h, activity, timeoutMs)
        }.onFailure {
            Log.e(TAG, "getLoginToken failed", it)
            onLog("getLoginToken exception: ${it.javaClass.simpleName}: ${it.message}")
            onResult(Result.failure(IllegalStateException(it.message ?: "本机号登录失败")))
        }
    }

    fun quitLoginPage() {
        runCatching {
            helper?.javaClass?.getMethod("quitLoginPage")?.invoke(helper)
        }
    }

    private fun enableSdkLogger() {
        runCatching {
            val reporter = helper!!.javaClass.getMethod("getReporter").invoke(helper)
            reporter.javaClass.getMethod("setLoggerEnable", Boolean::class.javaPrimitiveType)
                .invoke(reporter, true)
            Log.i(TAG, "Aliyun AuthSDK logger enabled")
        }.onFailure {
            Log.w(TAG, "enableSdkLogger failed: ${it.message}")
        }
    }

    private fun applyUiConfig(privacyUrl: String, termsUrl: String) {
        val h = helper ?: return
        val builderClass = Class.forName(UI_CONFIG_BUILDER)
        val builder = builderClass.getDeclaredConstructor().newInstance()
        builderClass.getMethod("setAppPrivacyOne", String::class.java, String::class.java)
            .invoke(builder, "《隐私政策》", privacyUrl)
        builderClass.getMethod("setAppPrivacyTwo", String::class.java, String::class.java)
            .invoke(builder, "《用户协议》", termsUrl)
        builderClass.getMethod("setAppPrivacyColor", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .invoke(builder, Color.GRAY, Color.parseColor("#C45C5C"))
        builderClass.getMethod("setSwitchAccHidden", Boolean::class.javaPrimitiveType)
            .invoke(builder, true)
        builderClass.getMethod("setLogBtnToastHidden", Boolean::class.javaPrimitiveType)
            .invoke(builder, true)
        builderClass.getMethod("setNavColor", Int::class.javaPrimitiveType)
            .invoke(builder, Color.parseColor("#C45C5C"))
        builderClass.getMethod("setStatusBarColor", Int::class.javaPrimitiveType)
            .invoke(builder, Color.parseColor("#C45C5C"))
        builderClass.getMethod("setLightColor", Boolean::class.javaPrimitiveType)
            .invoke(builder, true)
        builderClass.getMethod("setVendorPrivacyPrefix", String::class.java)
            .invoke(builder, "《")
        builderClass.getMethod("setVendorPrivacySuffix", String::class.java)
            .invoke(builder, "》")
        var orientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        if (Build.VERSION.SDK_INT == 26) {
            orientation = ActivityInfo.SCREEN_ORIENTATION_BEHIND
        }
        runCatching {
            builderClass.getMethod("setScreenOrientation", Int::class.javaPrimitiveType)
                .invoke(builder, orientation)
        }
        val config = builderClass.getMethod("create").invoke(builder)
        h.javaClass.getMethod("setAuthUIConfig", Class.forName(UI_CONFIG))
            .invoke(h, config)
    }

    private fun tokenListener(
        onLog: (String) -> Unit,
        onPayload: (JSONObject) -> Unit,
    ): Any {
        val listenerClass = Class.forName(TOKEN_LISTENER)
        return Proxy.newProxyInstance(
            listenerClass.classLoader,
            arrayOf(listenerClass),
        ) { _, method, args ->
            when (method.name) {
                "onTokenSuccess", "onTokenFailed" -> {
                    val raw = args?.getOrNull(0)?.toString().orEmpty()
                    Log.i(TAG, "${method.name}: $raw")
                    onLog("${method.name}: ${raw.take(300)}")
                    val payload = runCatching { JSONObject(raw) }.getOrElse {
                        JSONObject().put("msg", raw).put(
                            "code",
                            if (method.name == "onTokenFailed") "600011" else "",
                        )
                    }
                    if (!payload.has("code") && payload.has("resultCode")) {
                        payload.put("code", payload.optString("resultCode"))
                    }
                    mainHandler.post { onPayload(payload) }
                }
            }
            null
        }
    }

    private fun payloadCode(payload: JSONObject): String =
        payload.optString("code", payload.optString("resultCode", ""))

    private fun resultCodeConst(name: String): String = runCatching {
        Class.forName("com.mobile.auth.gatewayauth.ResultCode").getField(name).get(null) as String
    }.getOrDefault("")

    private fun secretPreview(secret: String): String {
        val trimmed = secret.trim()
        if (trimmed.length <= 16) return "len=${trimmed.length}"
        return "len=${trimmed.length} head=${trimmed.take(8)}…tail=${trimmed.takeLast(6)}"
    }

    private fun currentSigningMd5(): String = runCatching {
        val pm = appContext.packageManager
        val pkg = appContext.packageName
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val flags = PackageManager.GET_SIGNING_CERTIFICATES
            val info = pm.getPackageInfo(pkg, flags)
            val signingInfo = info.signingInfo ?: return@runCatching "?"
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
        }
        val sig = signatures?.firstOrNull() ?: return@runCatching "?"
        val digest = MessageDigest.getInstance("MD5").digest(sig.toByteArray())
        digest.joinToString("") { b -> "%02x".format(b) }
    }.getOrDefault("?")

    companion object {
        private const val TAG = "PnvsLogin"
        private const val HELPER = "com.mobile.auth.gatewayauth.PhoneNumberAuthHelper"
        private const val TOKEN_LISTENER = "com.mobile.auth.gatewayauth.TokenResultListener"
        private const val PRE_LOGIN_LISTENER = "com.mobile.auth.gatewayauth.PreLoginResultListener"
        private const val UI_CONFIG = "com.mobile.auth.gatewayauth.AuthUIConfig"
        private const val UI_CONFIG_BUILDER = "com.mobile.auth.gatewayauth.AuthUIConfig\$Builder"
    }
}
