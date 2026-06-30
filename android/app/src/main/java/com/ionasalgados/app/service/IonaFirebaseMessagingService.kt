package com.ionasalgados.app.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ionasalgados.app.data.notification.FcmTokenRegistrar
import com.ionasalgados.app.data.notification.NotificationHelper
import com.ionasalgados.app.domain.model.Pedido
import com.ionasalgados.app.domain.model.StatusPedido
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class IonaFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var fcmTokenRegistrar: FcmTokenRegistrar

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannels()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        fcmTokenRegistrar.registerToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data.isEmpty()) return

        val type = data["type"] ?: return
        val pedido = parsePedido(data) ?: return

        when (type) {
            "novo_pedido" -> notificationHelper.showNovoPedido(pedido)
            "pedido_recebido" -> notificationHelper.showPedidoRecebido(pedido)
            "pedido_cancelado" -> notificationHelper.showPedidoCancelado(pedido)
        }
    }

    private fun parsePedido(data: Map<String, String>): Pedido? {
        val id = data["pedido_id"]?.toLongOrNull() ?: return null
        val numero = data["numero"]?.toIntOrNull() ?: return null
        return Pedido(
            id = id,
            numero = numero,
            clienteId = 0,
            clienteNome = data["cliente_nome"]?.ifBlank { null },
            status = StatusPedido.NOVO,
            valorTotal = data["valor_total"]?.toDoubleOrNull() ?: 0.0
        )
    }
}
