package com.ionasalgados.app.data.remote

import com.ionasalgados.app.data.local.ServerConfigStore
import com.ionasalgados.app.data.remote.dto.*
import com.ionasalgados.app.domain.model.Pedido
import com.ionasalgados.app.domain.model.StatusPedido
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.json.JSONObject
import retrofit2.Response
import retrofit2.http.*
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

interface IonaApiService {
    @GET("produtos")
    suspend fun getProdutos(@Query("ativo") ativo: Boolean? = null): Response<List<ProdutoDto>>

    @GET("produtos/{id}")
    suspend fun getProduto(@Path("id") id: Long): Response<ProdutoDto>

    @POST("produtos")
    suspend fun createProduto(@Body produto: ProdutoDto): Response<ProdutoDto>

    @PUT("produtos/{id}")
    suspend fun updateProduto(@Path("id") id: Long, @Body produto: ProdutoDto): Response<ProdutoDto>

    @DELETE("produtos/{id}")
    suspend fun deleteProduto(@Path("id") id: Long): Response<Unit>

    @GET("categorias")
    suspend fun getCategorias(): Response<List<CategoriaDto>>

    @POST("categorias")
    suspend fun createCategoria(@Body categoria: CategoriaDto): Response<CategoriaDto>

    @GET("clientes")
    suspend fun getClientes(): Response<List<ClienteDto>>

    @DELETE("clientes/{id}")
    suspend fun deleteCliente(@Path("id") id: Long): Response<Unit>

    @GET("pedidos")
    suspend fun getPedidos(@Query("status") status: String? = null): Response<List<PedidoDto>>

    @GET("pedidos/{id}")
    suspend fun getPedido(@Path("id") id: Long): Response<PedidoDto>

    @POST("pedidos")
    suspend fun createPedido(@Body pedido: PedidoDto): Response<PedidoDto>

    @PATCH("pedidos/{id}/status")
    suspend fun updatePedidoStatus(@Path("id") id: Long, @Body body: Map<String, String>): Response<PedidoDto>

    @GET("dashboard")
    suspend fun getDashboard(): Response<DashboardDto>

    @GET("producao")
    suspend fun getProducao(@Query("data") data: String?): Response<ProducaoDto>

    @GET("financeiro/resumo")
    suspend fun getFinanceiroResumo(): Response<FinanceiroResumoDto>

    @GET("financeiro/caixa")
    suspend fun getCaixa(): Response<CaixaDto>

    @POST("financeiro/caixa/abrir")
    suspend fun abrirCaixa(@Body body: Map<String, Double>): Response<CaixaDto>

    @POST("financeiro/caixa/fechar")
    suspend fun fecharCaixa(@Body body: Map<String, String?>): Response<CaixaDto>

    @GET("financeiro/relatorio")
    suspend fun getRelatorio(
        @Query("periodo") periodo: String?,
        @Query("data_inicio") dataInicio: String?,
        @Query("data_fim") dataFim: String?
    ): Response<com.ionasalgados.app.data.repository.RelatorioDto>

    @GET("configuracoes")
    suspend fun getConfiguracoes(): Response<Map<String, String>>

    @PUT("configuracoes")
    suspend fun updateConfiguracoes(@Body config: Map<String, String>): Response<Map<String, String>>

    @GET("status")
    suspend fun getStatus(): Response<StatusDto>

    @GET("whatsapp/status")
    suspend fun getWhatsAppStatus(): Response<com.ionasalgados.app.data.repository.WhatsAppStatusDto>

    @POST("whatsapp/reconectar")
    suspend fun reconectarWhatsApp(@Body body: Map<String, String>): Response<com.ionasalgados.app.data.repository.WhatsAppStatusDto>

    @POST("whatsapp/desconectar")
    suspend fun desconectarWhatsApp(): Response<com.ionasalgados.app.data.repository.WhatsAppStatusDto>

    @POST("fcm/register")
    suspend fun registerFcmToken(@Body body: Map<String, String>): Response<Map<String, Boolean>>
}

@Singleton
class ApiProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val serverConfig: ServerConfigStore
) {
    @Volatile private var api: IonaApiService? = null
    @Volatile private var currentBaseUrl: String? = null

    suspend fun getApi(): IonaApiService {
        val baseUrl = serverConfig.apiBaseUrl(serverConfig.getServerUrl())
        if (api == null || currentBaseUrl != baseUrl) {
            synchronized(this) {
                if (api == null || currentBaseUrl != baseUrl) {
                    currentBaseUrl = baseUrl
                    api = Retrofit.Builder()
                        .baseUrl(baseUrl)
                        .client(okHttpClient)
                        .addConverterFactory(GsonConverterFactory.create(IonaGson.instance))
                        .build()
                        .create(IonaApiService::class.java)
                }
            }
        }
        return api!!
    }

    suspend fun resetApi() {
        synchronized(this) {
            api = null
            currentBaseUrl = null
        }
    }
}

@Singleton
class SocketService @Inject constructor(
    private val serverConfig: ServerConfigStore
) {
    private var socket: Socket? = null
    private val _events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SocketEvent> = _events

    suspend fun connect() {
        val serverUrl = serverConfig.getServerUrl().trimEnd('/')
        if (socket?.connected() == true) return

        try {
            val options = IO.Options().apply {
                reconnection = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = 3000
                timeout = 20000
            }
            socket = IO.socket(serverUrl, options)
            setupListeners()
            socket?.connect()
        } catch (e: Exception) {
            _events.tryEmit(SocketEvent.Disconnected)
        }
    }

    private fun setupListeners() {
        socket?.apply {
            on(Socket.EVENT_CONNECT) { _events.tryEmit(SocketEvent.Connected) }
            on(Socket.EVENT_DISCONNECT) { _events.tryEmit(SocketEvent.Disconnected) }
            on("novoPedido") { args -> parsePedido(args)?.let { _events.tryEmit(SocketEvent.NovoPedido(it)) } }
            on("pedidoAtualizado") { args -> parsePedido(args)?.let { _events.tryEmit(SocketEvent.PedidoAtualizado(it)) } }
            on("pedidoRecebido") { args -> parsePedido(args)?.let { _events.tryEmit(SocketEvent.PedidoRecebido(it)) } }
            on("pedidoCancelado") { args -> parsePedido(args)?.let { _events.tryEmit(SocketEvent.PedidoCancelado(it)) } }
            on("imprimirPedido") { args -> parsePedido(args)?.let { _events.tryEmit(SocketEvent.ImprimirPedido(it)) } }
            on("statusWhatsApp") { args ->
                val json = args.getOrNull(0) as? JSONObject
                val status = json?.optString("status") ?: "desconhecido"
                val code = json?.optString("pairingCode")
                _events.tryEmit(SocketEvent.WhatsAppStatus(status, code))
            }
            on("codigoWhatsApp") { args ->
                val json = args.getOrNull(0) as? JSONObject
                val code = json?.optString("code")
                _events.tryEmit(SocketEvent.WhatsAppCode(code))
            }
            on("novaMensagem") { _events.tryEmit(SocketEvent.NovaMensagem) }
        }
    }

    private fun parsePedido(args: Array<Any>): Pedido? {
        return try {
            val json = args[0] as JSONObject
            val itens = mutableListOf<com.ionasalgados.app.domain.model.ItemPedido>()
            json.optJSONArray("itens")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    itens.add(
                        com.ionasalgados.app.domain.model.ItemPedido(
                            id = item.optLong("id"),
                            pedidoId = item.optLong("pedido_id", json.optLong("id")),
                            produtoId = item.optLong("produto_id"),
                            nomeProduto = item.optString("nome_produto"),
                            quantidade = item.optInt("quantidade"),
                            precoUnitario = item.optDouble("preco_unitario"),
                            subtotal = item.optDouble("subtotal"),
                            observacao = item.optString("observacao").ifBlank { null }
                        )
                    )
                }
            }
            Pedido(
                id = json.optLong("id"),
                numero = json.optInt("numero"),
                clienteId = json.optLong("cliente_id"),
                clienteNome = json.optString("cliente_nome"),
                clienteTelefone = json.optString("cliente_telefone"),
                status = StatusPedido.fromString(json.optString("status", "novo")),
                valorItens = json.optDouble("valor_itens"),
                taxaEntrega = json.optDouble("taxa_entrega"),
                valorTotal = json.optDouble("valor_total"),
                endereco = json.optString("endereco"),
                formaPagamento = json.optString("forma_pagamento"),
                createdAt = json.optString("created_at"),
                itens = itens
            )
        } catch (_: Exception) {
            null
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
    }
}

sealed class SocketEvent {
    data object Connected : SocketEvent()
    data object Disconnected : SocketEvent()
    data object NovaMensagem : SocketEvent()
    data class NovoPedido(val pedido: Pedido) : SocketEvent()
    data class PedidoAtualizado(val pedido: Pedido) : SocketEvent()
    data class PedidoRecebido(val pedido: Pedido) : SocketEvent()
    data class PedidoCancelado(val pedido: Pedido) : SocketEvent()
    data class ImprimirPedido(val pedido: Pedido) : SocketEvent()
    data class WhatsAppStatus(val status: String, val code: String? = null) : SocketEvent()
    data class WhatsAppCode(val code: String?) : SocketEvent()
}
