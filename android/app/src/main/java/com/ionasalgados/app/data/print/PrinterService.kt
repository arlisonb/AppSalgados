package com.ionasalgados.app.data.print

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.ionasalgados.app.domain.model.Pedido
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrinterService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var printerAddress: String? = null

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    suspend fun connect(address: String): Boolean = withContext(Dispatchers.IO) {
        try {
            disconnect()
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@withContext false
            val device: BluetoothDevice = adapter.getRemoteDevice(address)
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket?.connect()
            outputStream = socket?.outputStream
            printerAddress = address
            updateConnectionState()
            socket?.isConnected == true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao conectar impressora", e)
            updateConnectionState()
            tryReconnect()
            false
        }
    }

    suspend fun printPedido(pedido: Pedido, config: Map<String, String> = emptyMap()) = withContext(Dispatchers.IO) {
        if (outputStream == null && printerAddress != null) {
            connect(printerAddress!!)
        }
        if (outputStream == null) {
            Log.w(TAG, "Impressora não conectada")
            return@withContext
        }

        try {
            val sb = StringBuilder()
            sb.appendLine(center("SALGADOS DA EMPRESA"))
            sb.appendLine(center(config["nome_empresa"] ?: "Iona Salgados"))
            sb.appendLine()
            sb.appendLine("Pedido Nº ${pedido.numero}")
            sb.appendLine("Data: ${pedido.createdAt?.substringBefore(" ") ?: ""}")
            sb.appendLine("Hora: ${pedido.createdAt?.substringAfter(" ")?.take(5) ?: ""}")
            sb.appendLine()
            sb.appendLine("Cliente: ${pedido.clienteNome ?: ""}")
            sb.appendLine("Telefone: ${pedido.clienteTelefone ?: ""}")
            sb.appendLine("Endereço: ${pedido.endereco ?: ""}")
            sb.appendLine()
            sb.appendLine("--------------------------------")
            sb.appendLine("Itens          Qtd      Valor")
            sb.appendLine("--------------------------------")

            pedido.itens.forEach { item ->
                val nome = item.nomeProduto.take(14).padEnd(14)
                val qtd = item.quantidade.toString().padStart(4)
                val valor = "R$ %.2f".format(item.subtotal).padStart(8)
                sb.appendLine("$nome $qtd $valor")
            }

            sb.appendLine("--------------------------------")
            sb.appendLine("TOTAL: R$ %.2f".format(pedido.valorTotal))
            sb.appendLine("Pagamento: ${pedido.formaPagamento ?: ""}")
            if (pedido.troco > 0) sb.appendLine("Troco: R$ %.2f".format(pedido.troco))
            sb.appendLine()
            sb.appendLine(center("Obrigado pela preferência!"))
            sb.appendLine()
            sb.appendLine()
            sb.appendLine()

            val bytes = buildEscPos(sb.toString())
            outputStream?.write(bytes)
            outputStream?.flush()
            updateConnectionState()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao imprimir", e)
            updateConnectionState()
            tryReconnect()
        }
    }

    private fun buildEscPos(text: String): ByteArray {
        val init = byteArrayOf(0x1B, 0x40)
        val cut = byteArrayOf(0x1D, 0x56, 0x00)
        return init + text.toByteArray(Charsets.UTF_8) + cut
    }

    private fun center(text: String): String {
        val width = 32
        val padding = ((width - text.length) / 2).coerceAtLeast(0)
        return " ".repeat(padding) + text
    }

    private suspend fun tryReconnect() {
        printerAddress?.let { connect(it) }
    }

    fun disconnect() {
        try {
            outputStream?.close()
            socket?.close()
        } catch (_: Exception) { }
        outputStream = null
        socket = null
        _connected.value = false
    }

    fun isConnected(): Boolean = socket?.isConnected == true

    private fun updateConnectionState() {
        _connected.value = socket?.isConnected == true
    }

    companion object {
        private const val TAG = "PrinterService"
    }
}
