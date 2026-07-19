package com.lover.app.core.auth

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.lang.reflect.Proxy
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

    fun isSdkAvailable(): Boolean = runCatching {
        Class.forName(HELPER)
        true
    }.getOrDefault(false)

    fun prepare(
        secretInfo: String,
        privacyUrl: String,
        termsUrl: String,
        onEnvReady: (Boolean) -> Unit,
    ) {
        if (!isSdkAvailable() || secretInfo.isBlank()) {
            onEnvReady(false)
            return
        }
        runCatching {
            val listener = tokenListener { /* ignore prepare callbacks */ }
            val getInstance = Class.forName(HELPER).getMethod(
                "getInstance",
                Context::class.java,
                Class.forName(TOKEN_LISTENER),
            )
            helper = getInstance.invoke(null, appContext.applicationContext, listener)
            helper!!.javaClass.getMethod("setAuthSDKInfo", String::class.java)
                .invoke(helper, secretInfo)
            applyUiConfig(privacyUrl, termsUrl)
            val serviceType = Class.forName(HELPER).getField("SERVICE_TYPE_LOGIN").getInt(null)
            helper!!.javaClass.getMethod("checkEnvAvailable", Int::class.javaPrimitiveType)
                .invoke(helper, serviceType)
            runCatching {
                val preLogin = Class.forName(PRE_LOGIN_LISTENER)
                val accelerateListener = Proxy.newProxyInstance(
                    preLogin.classLoader,
                    arrayOf(preLogin),
                ) { _, _, _ -> null }
                helper!!.javaClass.getMethod(
                    "accelerateLoginPage",
                    Int::class.javaPrimitiveType,
                    preLogin,
                ).invoke(helper, 5_000, accelerateListener)
            }
            mainHandler.post { onEnvReady(true) }
        }.onFailure {
            helper = null
            mainHandler.post { onEnvReady(false) }
        }
    }

    fun getLoginToken(
        activity: Activity,
        timeoutMs: Int = 8_000,
        onResult: (Result<String>) -> Unit,
    ) {
        val h = helper
        if (h == null) {
            onResult(Result.failure(IllegalStateException("本机号登录不可用，请使用短信验证码")))
            return
        }
        runCatching {
            val listener = tokenListener { payload ->
                val code = payload.optString("code", payload.optString("_code", ""))
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
                            onResult(
                                Result.failure(
                                    IllegalStateException(payload.optString("msg", "本机号登录失败，请使用短信验证码")),
                                ),
                            )
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
            onResult(Result.failure(IllegalStateException(it.message ?: "本机号登录失败")))
        }
    }

    fun quitLoginPage() {
        runCatching {
            helper?.javaClass?.getMethod("quitLoginPage")?.invoke(helper)
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

    private fun tokenListener(onPayload: (JSONObject) -> Unit): Any {
        val listenerClass = Class.forName(TOKEN_LISTENER)
        return Proxy.newProxyInstance(
            listenerClass.classLoader,
            arrayOf(listenerClass),
        ) { _, method, args ->
            when (method.name) {
                "onTokenSuccess", "onTokenFailed" -> {
                    val raw = args?.getOrNull(0)?.toString().orEmpty()
                    val payload = runCatching { JSONObject(raw) }.getOrElse {
                        JSONObject().put("msg", raw).put("code", if (method.name == "onTokenFailed") "600011" else "")
                    }
                    // Aliyun may nest fields differently across versions
                    if (!payload.has("code") && payload.has("resultCode")) {
                        payload.put("code", payload.optString("resultCode"))
                    }
                    mainHandler.post { onPayload(payload) }
                }
            }
            null
        }
    }

    companion object {
        private const val HELPER = "com.mobile.auth.gatewayauth.PhoneNumberAuthHelper"
        private const val TOKEN_LISTENER = "com.mobile.auth.gatewayauth.TokenResultListener"
        private const val PRE_LOGIN_LISTENER = "com.mobile.auth.gatewayauth.PreLoginResultListener"
        private const val UI_CONFIG = "com.mobile.auth.gatewayauth.AuthUIConfig"
        private const val UI_CONFIG_BUILDER = "com.mobile.auth.gatewayauth.AuthUIConfig\$Builder"
    }
}
