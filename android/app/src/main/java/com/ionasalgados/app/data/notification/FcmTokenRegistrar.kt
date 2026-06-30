package com.ionasalgados.app.data.notification

import android.os.Build
import com.ionasalgados.app.data.remote.ApiProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FcmTokenRegistrar @Inject constructor(
    private val apiProvider: ApiProvider
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun registerToken(token: String) {
        scope.launch {
            try {
                val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
                apiProvider.getApi().registerFcmToken(
                    mapOf(
                        "token" to token,
                        "device_name" to deviceName
                    )
                )
            } catch (_: Exception) { }
        }
    }
}
