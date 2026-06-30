package com.ionasalgados.app.data.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ionasalgados.app.R
import com.ionasalgados.app.domain.model.Pedido
import com.ionasalgados.app.presentation.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val manager get() = NotificationManagerCompat.from(context)

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val pedidos = NotificationChannel(
            CHANNEL_PEDIDOS,
            context.getString(R.string.notif_channel_pedidos),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_pedidos_desc)
            enableVibration(true)
        }

        val entrega = NotificationChannel(
            CHANNEL_ENTREGA,
            context.getString(R.string.notif_channel_entrega),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notif_channel_entrega_desc)
        }

        val monitor = NotificationChannel(
            CHANNEL_MONITOR,
            context.getString(R.string.notif_channel_monitor),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notif_channel_monitor_desc)
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(pedidos)
        nm.createNotificationChannel(entrega)
        nm.createNotificationChannel(monitor)
    }

    fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.notif_monitor_title))
            .setContentText(context.getString(R.string.notif_monitor_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent())
            .build()
    }

    fun showNovoPedido(pedido: Pedido) {
        val cliente = pedido.clienteNome ?: "Cliente"
        val valor = "R$ %.2f".format(pedido.valorTotal)
        val notification = NotificationCompat.Builder(context, CHANNEL_PEDIDOS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.notif_novo_pedido_title, pedido.numero))
            .setContentText("$cliente — $valor")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$cliente — $valor"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(openAppIntent(pedido.id))
            .build()

        notify(pedido.id.toInt(), notification)
    }

    fun showPedidoRecebido(pedido: Pedido) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ENTREGA)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.notif_pedido_recebido_title, pedido.numero))
            .setContentText(context.getString(R.string.notif_pedido_recebido_text, pedido.clienteNome ?: "Cliente"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(pedido.id))
            .build()

        notify(NOTIFY_PEDIDO_RECEBIDO_BASE + pedido.id.toInt(), notification)
    }

    fun showPedidoCancelado(pedido: Pedido) {
        val notification = NotificationCompat.Builder(context, CHANNEL_PEDIDOS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.notif_pedido_cancelado_title, pedido.numero))
            .setContentText(pedido.clienteNome ?: "Cliente")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()

        notify(NOTIFY_PEDIDO_CANCELADO_BASE + pedido.id.toInt(), notification)
    }

    fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun openAppIntent(pedidoId: Long? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            pedidoId?.let { putExtra(EXTRA_PEDIDO_ID, it) }
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, pedidoId?.toInt() ?: 0, intent, flags)
    }

    private fun notify(id: Int, notification: Notification) {
        if (!canNotify()) return
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) { }
    }

    companion object {
        const val CHANNEL_PEDIDOS = "pedidos"
        const val CHANNEL_ENTREGA = "entrega"
        const val CHANNEL_MONITOR = "monitor"
        const val EXTRA_PEDIDO_ID = "pedido_id"
        const val FOREGROUND_NOTIFICATION_ID = 1001
        private const val NOTIFY_PEDIDO_RECEBIDO_BASE = 20000
        private const val NOTIFY_PEDIDO_CANCELADO_BASE = 30000
    }
}
