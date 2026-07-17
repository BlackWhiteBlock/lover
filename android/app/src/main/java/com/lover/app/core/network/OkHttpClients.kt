package com.lover.app.core.network

import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionSpec
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.TlsVersion

/**
 * Shared OkHttp defaults for talking to nginx / Baota reverse proxies / Qiniu CDN.
 *
 * Prefer IPv4; force HTTP/1.1; use browser-like UA; prefer TLS 1.2 on device
 * networks that reset non-browser TLS 1.3 handshakes before they reach nginx.
 *
 * API and Coil must share these transport defaults. Release forbids cleartext;
 * Coil previously used the platform stack and could fail while Retrofit still worked.
 */
object OkHttpClients {
    private val ipv4PreferredDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = Dns.SYSTEM.lookup(hostname)
            val ipv4 = addresses.filterIsInstance<Inet4Address>()
            return ipv4.ifEmpty { addresses }
        }
    }

    private val tls12 = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
        .tlsVersions(TlsVersion.TLS_1_2)
        .build()

    private fun browserLikeHeaders(accept: String) = Interceptor { chain ->
        chain.proceed(
            chain.request().newBuilder()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36 LoverApp/1.0",
                )
                .header("Accept", accept)
                .build(),
        )
    }

    private fun baseBuilder(accept: String): OkHttpClient.Builder =
        OkHttpClient.Builder()
            .dns(ipv4PreferredDns)
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectionSpecs(listOf(tls12, ConnectionSpec.CLEARTEXT))
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(browserLikeHeaders(accept))

    /** Retrofit / JSON APIs. */
    fun builder(): OkHttpClient.Builder =
        baseBuilder("application/json, text/plain, */*")

    /** Coil / media downloads — do not send Accept: application/json. */
    fun mediaBuilder(): OkHttpClient.Builder =
        baseBuilder("image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
}
