package com.lover.app.core.media

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import coil.request.ErrorResult
import coil.request.ImageRequest
import com.lover.app.BuildConfig
import com.lover.app.core.network.OkHttpClients
import java.lang.ref.WeakReference
import java.util.ArrayDeque
import okhttp3.Request
import kotlin.concurrent.thread

/**
 * Release 也可用于定位媒体签名 / Coil 拉图失败。
 * Logcat 过滤：`adb logcat -s LoverMedia`
 */
object MediaLoadDiagnostics {
    const val TAG = "LoverMedia"

    /** 排障期间保持 true；确认修复后可改 false。 */
    @Volatile
    var enabled: Boolean = true

    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val events = ArrayDeque<String>(24)
    private var app: Application? = null
    private var currentActivity: WeakReference<Activity>? = null
    private var autoDialogShown = false
    private var probing = false

    fun install(application: Application) {
        app = application
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    currentActivity = WeakReference(activity)
                }

                override fun onActivityPaused(activity: Activity) {
                    if (currentActivity?.get() === activity) currentActivity = null
                }

                override fun onActivityCreated(a: Activity, b: Bundle?) = Unit
                override fun onActivityStarted(a: Activity) = Unit
                override fun onActivityStopped(a: Activity) = Unit
                override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
                override fun onActivityDestroyed(a: Activity) = Unit
            },
        )
        note(
            "boot",
            "debug=${BuildConfig.DEBUG} api=${BuildConfig.API_BASE_URL} cleartextDiag=on",
            showDialog = false,
        )
    }

    fun note(stage: String, detail: String, showDialog: Boolean = false) {
        if (!enabled) return
        val line = "[$stage] $detail"
        Log.e(TAG, line)
        synchronized(lock) {
            if (events.size >= 24) events.removeFirst()
            events.addLast(line)
        }
        if (showDialog) maybeAutoDialog(line)
    }

    fun onSignFailed(assetId: String, variant: String, error: Throwable) {
        note(
            "sign",
            "asset=$assetId variant=$variant err=${error.javaClass.simpleName}: ${error.message}",
            showDialog = true,
        )
    }

    fun onSignEmpty(assetId: String) {
        note("sign", "asset=$assetId → empty url (签名失败被吞或无权限)", showDialog = true)
    }

    fun onCoilError(request: ImageRequest, result: ErrorResult) {
        val raw = request.data?.toString().orEmpty()
        val safe = redactUrl(raw)
        val err = result.throwable
        note(
            "coil",
            "url=$safe err=${err.javaClass.simpleName}: ${err.message}",
            showDialog = true,
        )
        probeOnce(raw)
    }

    fun snapshot(): String = synchronized(lock) {
        if (events.isEmpty()) "(暂无记录)" else events.joinToString("\n")
    }

    fun showPanel() {
        val body = snapshot()
        mainHandler.post {
            val activity = currentActivity?.get()
            if (activity == null || activity.isFinishing) {
                app?.let {
                    Toast.makeText(it, body.take(180), Toast.LENGTH_LONG).show()
                }
                return@post
            }
            AlertDialog.Builder(activity)
                .setTitle("媒体诊断 (LoverMedia)")
                .setMessage(body)
                .setPositiveButton("复制") { _, _ ->
                    copyText(activity, body)
                    Toast.makeText(activity, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                }
                .setNeutralButton("清空") { _, _ ->
                    synchronized(lock) { events.clear() }
                    autoDialogShown = false
                }
                .setNegativeButton("关闭", null)
                .show()
        }
    }

    fun copySnapshot(context: Context) {
        copyText(context, snapshot())
        Toast.makeText(context, "诊断日志已复制", Toast.LENGTH_SHORT).show()
    }

    private fun maybeAutoDialog(latest: String) {
        if (autoDialogShown) return
        autoDialogShown = true
        mainHandler.post {
            val activity = currentActivity?.get()
            if (activity == null || activity.isFinishing) {
                app?.let {
                    Toast.makeText(it, "媒体失败: ${latest.take(160)}", Toast.LENGTH_LONG).show()
                }
                return@post
            }
            AlertDialog.Builder(activity)
                .setTitle("媒体加载失败")
                .setMessage(
                    "$latest\n\n完整日志可在「我们 → 媒体诊断」查看/复制。\nLogcat: adb logcat -s LoverMedia",
                )
                .setPositiveButton("查看全部") { _, _ -> showPanel() }
                .setNegativeButton("知道了", null)
                .show()
        }
    }

    private fun probeOnce(url: String) {
        if (url.isBlank() || probing) return
        probing = true
        thread(name = "lover-media-probe") {
            val safe = redactUrl(url)
            try {
                note("probe", "start scheme=${url.substringBefore("://", "?")} url=$safe", showDialog = false)
                val client = OkHttpClients.mediaBuilder()
                    .followRedirects(true)
                    .build()
                val request = Request.Builder()
                    .url(url)
                    .header("Range", "bytes=0-0")
                    .get()
                    .build()
                client.newCall(request).execute().use { resp ->
                    note(
                        "probe",
                        "http=${resp.code} ct=${resp.header("Content-Type")} len=${resp.header("Content-Length")} url=$safe",
                        showDialog = false,
                    )
                }
            } catch (error: Throwable) {
                note(
                    "probe",
                    "${error.javaClass.simpleName}: ${error.message} url=$safe",
                    showDialog = true,
                )
            }
        }
    }

    private fun copyText(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("LoverMedia", text))
    }

    private fun redactUrl(url: String): String {
        if (url.isBlank()) return "(empty)"
        return try {
            val noToken = url
                .replace(Regex("""([?&])(token|e|sign|Signature|Expires)=[^&]*""", RegexOption.IGNORE_CASE), "$1$2=***")
            if (noToken.length <= 160) noToken else noToken.take(160) + "…"
        } catch (_: Throwable) {
            url.take(80)
        }
    }
}
