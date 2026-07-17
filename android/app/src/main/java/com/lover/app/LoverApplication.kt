package com.lover.app

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.Logger
import com.lover.app.core.media.MediaLoadDiagnostics
import com.lover.app.core.network.OkHttpClients
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LoverApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        MediaLoadDiagnostics.install(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient { OkHttpClients.mediaBuilder().build() }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_image_cache"))
                    .maxSizeBytes(128L * 1024 * 1024)
                    .build()
            }
            .logger(
                object : Logger {
                    override var level: Int = Log.VERBOSE

                    override fun log(
                        tag: String,
                        priority: Int,
                        message: String?,
                        throwable: Throwable?,
                    ) {
                        if (!MediaLoadDiagnostics.enabled) return
                        if (priority < Log.WARN && throwable == null) return
                        Log.println(
                            priority.coerceAtLeast(Log.DEBUG),
                            MediaLoadDiagnostics.TAG,
                            "coil/$tag ${message.orEmpty()}" +
                                (throwable?.let { " | ${it.javaClass.simpleName}: ${it.message}" } ?: ""),
                        )
                    }
                },
            )
            .crossfade(true)
            .build()
}
