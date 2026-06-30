package com.ionasalgados.app.service

import android.content.Intent
import android.os.IBinder
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.ionasalgados.app.data.notification.NotificationHelper
import com.ionasalgados.app.data.remote.SocketEvent
import com.ionasalgados.app.data.remote.SocketService
import com.ionasalgados.app.data.repository.PedidoRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OrderMonitorService : LifecycleService() {

    @Inject lateinit var socketService: SocketService
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var pedidoRepository: PedidoRepository

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannels()
        startForeground(
            NotificationHelper.FOREGROUND_NOTIFICATION_ID,
            notificationHelper.buildForegroundNotification()
        )

        lifecycleScope.launch {
            socketService.connect()
            socketService.events.collect { event ->
                when (event) {
                    is SocketEvent.NovoPedido -> {
                        lifecycleScope.launch {
                            pedidoRepository.cachePedido(event.pedido)
                        }
                        notificationHelper.showNovoPedido(event.pedido)
                    }
                    is SocketEvent.PedidoRecebido -> {
                        notificationHelper.showPedidoRecebido(event.pedido)
                    }
                    is SocketEvent.PedidoCancelado -> {
                        notificationHelper.showPedidoCancelado(event.pedido)
                    }
                    else -> Unit
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)
}
