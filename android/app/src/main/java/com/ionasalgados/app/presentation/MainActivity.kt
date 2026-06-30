package com.ionasalgados.app.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.ionasalgados.app.data.notification.FcmTokenRegistrar
import com.ionasalgados.app.data.notification.NotificationHelper
import com.ionasalgados.app.presentation.navigation.IonaNavHost
import com.ionasalgados.app.presentation.theme.IonaTheme
import com.ionasalgados.app.service.OrderMonitorService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var fcmTokenRegistrar: FcmTokenRegistrar

    private val pedidoDeepLink = mutableStateOf<Long?>(null)

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        startOrderMonitor()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pedidoDeepLink.value = intent?.getLongExtra(NotificationHelper.EXTRA_PEDIDO_ID, -1L)
            ?.takeIf { it > 0L }

        enableEdgeToEdge()
        setContent {
            IonaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    IonaNavHost(initialPedidoId = pedidoDeepLink.value)
                }
            }
        }

        ensureNotificationsAndStartMonitor()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getLongExtra(NotificationHelper.EXTRA_PEDIDO_ID, -1L)
            .takeIf { it > 0L }
            ?.let { pedidoDeepLink.value = it }
    }

    private fun ensureNotificationsAndStartMonitor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                startOrderMonitor()
            } else {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startOrderMonitor()
        }
    }

    private fun startOrderMonitor() {
        val intent = Intent(this, OrderMonitorService::class.java)
        ContextCompat.startForegroundService(this, intent)
        registerFcmToken()
    }

    private fun registerFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                task.result?.let { fcmTokenRegistrar.registerToken(it) }
            }
        }
    }
}
