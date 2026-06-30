package com.ionasalgados.app.data.remote

import com.ionasalgados.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class ServerDiscoveryService @Inject constructor(
    okHttpClient: OkHttpClient
) {
    private val probeClient = okHttpClient.newBuilder()
        .connectTimeout(600, TimeUnit.MILLISECONDS)
        .readTimeout(600, TimeUnit.MILLISECONDS)
        .callTimeout(1, TimeUnit.SECONDS)
        .build()

    suspend fun discover(): String? = withContext(Dispatchers.IO) {
        val subnet = getLocalSubnet() ?: return@withContext null
        val found = CompletableDeferred<String?>()
        val semaphore = Semaphore(40)

        coroutineScope {
            (1..254).forEach { host ->
                launch {
                    if (found.isCompleted) return@launch
                    semaphore.withPermit {
                        if (found.isCompleted) return@withPermit
                        val url = "http://$subnet.$host:3000"
                        if (isIonaServer(url)) found.complete(url)
                    }
                }
            }
            withTimeoutOrNull(20_000) { found.await() }
        }
    }

    suspend fun probe(url: String): Boolean = withContext(Dispatchers.IO) {
        isIonaServer(url.trimEnd('/'))
    }

    fun isProductionMode(): Boolean = BuildConfig.USE_PRODUCTION_SERVER

    suspend fun resolveServerUrl(storedUrl: String): String = withContext(Dispatchers.IO) {
        if (BuildConfig.USE_PRODUCTION_SERVER) {
            return@withContext BuildConfig.SOCKET_URL.trimEnd('/')
        }
        var url = storedUrl.trimEnd('/')
        if (!isIonaServer(url)) {
            discover()?.let { url = it }
        }
        url
    }

    private fun isIonaServer(baseUrl: String): Boolean {
        return try {
            val request = Request.Builder().url("$baseUrl/api/status").get().build()
            probeClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val body = response.body?.string() ?: return false
                body.contains("\"servidor\":\"online\"") || body.contains("\"app\":\"iona-salgados\"")
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun getLocalSubnet(): String? {
        try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { ni ->
                if (!ni.isUp || ni.isLoopback) return@forEach
                ni.inetAddresses.toList().forEach { addr ->
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: return@forEach
                        if (!host.startsWith("169.254.")) {
                            return host.substringBeforeLast(".")
                        }
                    }
                }
            }
        } catch (_: Exception) { }
        return null
    }
}
