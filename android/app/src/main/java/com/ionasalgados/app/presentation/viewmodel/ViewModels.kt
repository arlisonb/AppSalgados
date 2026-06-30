package com.ionasalgados.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ionasalgados.app.data.local.ServerConfigStore
import com.ionasalgados.app.data.print.PrinterService
import com.ionasalgados.app.data.remote.ApiProvider
import com.ionasalgados.app.data.remote.ServerDiscoveryService
import com.ionasalgados.app.data.remote.SocketEvent
import com.ionasalgados.app.data.remote.SocketService
import com.ionasalgados.app.data.remote.dto.*
import com.ionasalgados.app.data.repository.*
import com.ionasalgados.app.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val socketService: SocketService,
    private val pedidoRepository: PedidoRepository,
    private val produtoRepository: ProdutoRepository,
    private val dashboardRepository: DashboardRepository,
    private val configRepository: ConfigRepository,
    private val printerService: PrinterService,
    private val serverConfig: ServerConfigStore,
    private val apiProvider: ApiProvider,
    private val serverDiscovery: ServerDiscoveryService
) : ViewModel() {

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _whatsappStatus = MutableStateFlow("")
    val whatsappStatus: StateFlow<String> = _whatsappStatus.asStateFlow()

    private val _pairingCode = MutableStateFlow<String?>(null)
    val pairingCode: StateFlow<String?> = _pairingCode.asStateFlow()

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _discovering = MutableStateFlow(false)
    val discovering: StateFlow<Boolean> = _discovering.asStateFlow()

    private val _notification = MutableStateFlow<String?>(null)
    val notification = _notification.asStateFlow()

    val printerConnected: StateFlow<Boolean> = printerService.connected

    init {
        viewModelScope.launch {
            ensureServerConnection()
            connectSocket()
            refreshData()
            syncPrinterStatus()
        }
    }

    private suspend fun ensureServerConnection() {
        _discovering.value = !serverDiscovery.isProductionMode()
        _connectionStatus.value = ConnectionStatus.RECONNECTING
        val url = serverDiscovery.resolveServerUrl(serverConfig.getServerUrl())
        if (serverDiscovery.probe(url) || serverDiscovery.isProductionMode()) {
            serverConfig.setServerUrl(url)
            apiProvider.resetApi()
        }
        _serverUrl.value = url
        _discovering.value = false
    }

    fun rediscoverServer() {
        viewModelScope.launch {
            ensureServerConnection()
            socketService.disconnect()
            socketService.connect()
            refreshData()
        }
    }

    private fun connectSocket() {
        viewModelScope.launch {
            socketService.connect()
            socketService.events.collect { event ->
                when (event) {
                    is SocketEvent.Connected -> _connectionStatus.value = ConnectionStatus.CONNECTED
                    is SocketEvent.Disconnected -> _connectionStatus.value = ConnectionStatus.DISCONNECTED
                    is SocketEvent.NovoPedido -> {
                        viewModelScope.launch {
                            pedidoRepository.cachePedido(event.pedido)
                            refreshData()
                        }
                        printerService.printPedido(event.pedido)
                    }
                    is SocketEvent.ImprimirPedido -> printerService.printPedido(event.pedido)
                    is SocketEvent.PedidoAtualizado -> refreshData()
                    is SocketEvent.PedidoCancelado -> refreshData()
                    is SocketEvent.PedidoRecebido -> {
                        refreshData()
                        _notification.value = "Pedido #${event.pedido.numero} recebido pelo cliente!"
                    }
                    is SocketEvent.WhatsAppStatus -> {
                        _whatsappStatus.value = event.status
                        event.code?.let { _pairingCode.value = it }
                    }
                    is SocketEvent.WhatsAppCode -> _pairingCode.value = event.code
                    is SocketEvent.NovaMensagem -> { }
                }
            }
        }
    }

    fun clearNotification() {
        _notification.value = null
    }

    fun refreshPrinterStatus() {
        viewModelScope.launch { syncPrinterStatus() }
    }

    private suspend fun syncPrinterStatus() {
        val mac = serverConfig.getPrinterMac()
        if (mac.isNotBlank() && !printerService.isConnected()) {
            printerService.connect(mac)
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            _connectionStatus.value = ConnectionStatus.RECONNECTING
            if (configRepository.testConnection()) {
                pedidoRepository.refreshPedidos()
                produtoRepository.refresh()
                _connectionStatus.value = ConnectionStatus.CONNECTED
                val wa = configRepository.getWhatsAppStatus()
                _whatsappStatus.value = wa?.status ?: "desconhecido"
                _pairingCode.value = wa?.pairingCode
                syncPrinterStatus()
            } else {
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
            }
        }
    }
}

enum class ConnectionStatus { CONNECTED, DISCONNECTED, RECONNECTING }

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository,
    private val pedidoRepository: PedidoRepository,
    private val printerService: PrinterService
) : ViewModel() {
    private val _dashboard = MutableStateFlow(Dashboard())
    val dashboard = _dashboard.asStateFlow()
    val pedidos = pedidoRepository.getPedidosHoje()
    private val _message = MutableStateFlow("")
    val message = _message.asStateFlow()

    init { loadDashboard() }

    fun loadDashboard() {
        viewModelScope.launch {
            pedidoRepository.refreshPedidos()
            _dashboard.value = dashboardRepository.getDashboard()
        }
    }

    fun imprimirPedido(id: Long) {
        viewModelScope.launch {
            val pedido = pedidoRepository.getPedido(id)
            if (pedido == null) {
                _message.value = "Pedido não encontrado"
                return@launch
            }
            printerService.printPedido(pedido)
            _message.value = if (printerService.isConnected()) {
                "Pedido #${pedido.numero} enviado para impressão!"
            } else {
                "Impressora não conectada. Configure em Configurações."
            }
        }
    }

    fun clearMessage() {
        _message.value = ""
    }
}

@HiltViewModel
class PedidosViewModel @Inject constructor(
    private val pedidoRepository: PedidoRepository,
    private val socketService: SocketService
) : ViewModel() {
    val pedidos = pedidoRepository.getPedidosAtivos()

    init {
        refresh()
        viewModelScope.launch {
            socketService.connect()
            socketService.events.collect { event ->
                when (event) {
                    is SocketEvent.NovoPedido -> {
                        pedidoRepository.cachePedido(event.pedido)
                        refresh()
                    }
                    is SocketEvent.PedidoAtualizado,
                    is SocketEvent.PedidoCancelado,
                    is SocketEvent.PedidoRecebido -> refresh()
                    else -> {}
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { pedidoRepository.refreshPedidos() }
    }
}

@HiltViewModel
class PedidoDetalheViewModel @Inject constructor(
    private val pedidoRepository: PedidoRepository,
    private val printerService: PrinterService,
    private val socketService: SocketService
) : ViewModel() {
    private val _pedido = MutableStateFlow<Pedido?>(null)
    val pedido = _pedido.asStateFlow()
    private val _message = MutableStateFlow("")
    val message = _message.asStateFlow()
    private var pedidoIdAtual = 0L

    init {
        viewModelScope.launch {
            socketService.connect()
            socketService.events.collect { event ->
                if (event is SocketEvent.PedidoRecebido && event.pedido.id == pedidoIdAtual) {
                    _pedido.value = event.pedido
                    _message.value = "Pedido #${event.pedido.numero} confirmado pelo cliente!"
                }
            }
        }
    }

    fun loadPedido(id: Long) {
        pedidoIdAtual = id
        viewModelScope.launch { _pedido.value = pedidoRepository.getPedido(id) }
    }

    fun updateStatus(id: Long, status: StatusPedido) {
        viewModelScope.launch {
            pedidoRepository.updateStatus(id, status)
                .onSuccess {
                    _pedido.value = it
                    _message.value = when (status) {
                        StatusPedido.SAIU_ENTREGA -> "Cliente avisado no WhatsApp!"
                        StatusPedido.FINALIZADO -> "Pedido marcado como entregue!"
                        else -> ""
                    }
                }
                .onFailure { _message.value = "Erro ao atualizar status" }
        }
    }

    fun reimprimir() {
        viewModelScope.launch {
            val pedido = _pedido.value ?: return@launch
            printerService.printPedido(pedido)
            _message.value = if (printerService.isConnected()) {
                "Enviado para impressão!"
            } else {
                "Impressora não conectada. Configure em Configurações."
            }
        }
    }
}

@HiltViewModel
class CardapioViewModel @Inject constructor(
    private val produtoRepository: ProdutoRepository
) : ViewModel() {
    val produtos = produtoRepository.getProdutos()
    val categorias = produtoRepository.getCategorias()

    private val _message = MutableStateFlow("")
    val message = _message.asStateFlow()

    init { viewModelScope.launch { produtoRepository.refresh() } }

    fun refresh() {
        viewModelScope.launch { produtoRepository.refresh() }
    }

    fun adicionarProduto(nome: String, categoriaNome: String, precoUnidade: Double, precoCento: Double, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _message.value = "Salvando..."
            if (!produtoRepository.ensureServerConnection()) {
                _message.value = "Servidor offline. Vá em Configurações → Buscar servidor."
                onResult(false)
                return@launch
            }
            produtoRepository.createProduto(nome, categoriaNome, precoUnidade, precoCento)
                .onSuccess {
                    _message.value = "Produto salvo!"
                    onResult(true)
                }
                .onFailure {
                    _message.value = "Erro ao salvar. Verifique se PC e celular estão na mesma Wi-Fi."
                    onResult(false)
                }
        }
    }

    fun excluirProduto(id: Long) {
        viewModelScope.launch {
            produtoRepository.deleteProduto(id)
                .onSuccess { _message.value = "Produto excluído" }
                .onFailure { _message.value = "Erro ao excluir produto" }
        }
    }
}

@HiltViewModel
class ProducaoViewModel @Inject constructor(
    private val pedidoRepository: PedidoRepository,
    private val dashboardRepository: DashboardRepository
) : ViewModel() {
    val pedidos = pedidoRepository.getPedidosHoje()
    private val _producao = MutableStateFlow<ProducaoDto?>(null)
    val producao = _producao.asStateFlow()

    init {
        viewModelScope.launch {
            pedidoRepository.refreshPedidos()
            _producao.value = dashboardRepository.getProducao()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            pedidoRepository.refreshPedidos()
            _producao.value = dashboardRepository.getProducao()
        }
    }
}

@HiltViewModel
class FinanceiroViewModel @Inject constructor(
    private val financeiroRepository: FinanceiroRepository,
    private val dashboardRepository: DashboardRepository
) : ViewModel() {
    private val _resumo = MutableStateFlow<FinanceiroResumoDto?>(null)
    val resumo = _resumo.asStateFlow()
    private val _caixa = MutableStateFlow<CaixaDto?>(null)
    val caixa = _caixa.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _resumo.value = financeiroRepository.getResumo()
            _caixa.value = financeiroRepository.getCaixa()
        }
    }

    fun abrirCaixa(valor: Double) {
        viewModelScope.launch {
            financeiroRepository.abrirCaixa(valor).onSuccess { refresh() }
        }
    }

    fun fecharCaixa(obs: String) {
        viewModelScope.launch {
            financeiroRepository.fecharCaixa(obs).onSuccess { refresh() }
        }
    }
}

@HiltViewModel
class RelatoriosViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository
) : ViewModel() {
    private val _relatorio = MutableStateFlow<RelatorioDto?>(null)
    val relatorio = _relatorio.asStateFlow()
    private val _dashboard = MutableStateFlow(Dashboard())
    val dashboard = _dashboard.asStateFlow()

    init { loadRelatorio("diario") }

    fun loadRelatorio(periodo: String) {
        viewModelScope.launch {
            _relatorio.value = dashboardRepository.getRelatorio(periodo)
            _dashboard.value = dashboardRepository.getDashboard()
        }
    }
}

@HiltViewModel
class WhatsAppViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
    private val socketService: SocketService
) : ViewModel() {
    private val _whatsappStatus = MutableStateFlow("desconhecido")
    val whatsappStatus = _whatsappStatus.asStateFlow()

    private val _pairingCode = MutableStateFlow<String?>(null)
    val pairingCode = _pairingCode.asStateFlow()

    private val _config = MutableStateFlow<Map<String, String>>(emptyMap())
    val config = _config.asStateFlow()

    private val _message = MutableStateFlow("")
    val message = _message.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    init {
        viewModelScope.launch {
            socketService.connect()
            socketService.events.collect { event ->
                when (event) {
                    is SocketEvent.WhatsAppStatus -> {
                        _whatsappStatus.value = event.status
                        event.code?.let { _pairingCode.value = it }
                        _isConnected.value = event.status in listOf("conectado", "isLogged", "inChat")
                    }
                    is SocketEvent.WhatsAppCode -> {
                        _pairingCode.value = event.code
                        _whatsappStatus.value = "aguardando_codigo"
                    }
                    else -> { }
                }
            }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _config.value = configRepository.getConfiguracoes()
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            configRepository.getWhatsAppStatus()?.let {
                _whatsappStatus.value = it.status ?: "desconhecido"
                _pairingCode.value = it.pairingCode
                _isConnected.value = it.status in listOf("conectado", "isLogged", "inChat")
            }
        }
    }

    fun gerarCodigo(telefone: String) {
        var numero = telefone.replace(Regex("\\D"), "")
        if (numero.length in 10..11 && !numero.startsWith("55")) {
            numero = "55$numero"
        }
        if (numero.length < 12) {
            _message.value = "Informe o número com DDI (ex: 5534996677668)"
            return
        }
        viewModelScope.launch {
            _message.value = "Gerando código..."
            _pairingCode.value = null
            configRepository.saveConfiguracoes(mapOf("whatsapp" to numero))
            val result = configRepository.reconectarWhatsApp(numero)
            result.onSuccess {
                _whatsappStatus.value = it.status ?: "aguardando_codigo"
                if (it.pairingCode != null) {
                    _pairingCode.value = it.pairingCode
                    _message.value = "Código gerado! Digite no WhatsApp."
                } else {
                    aguardarCodigo()
                }
            }.onFailure {
                aguardarCodigo()
            }
        }
    }

    private fun aguardarCodigo() {
        viewModelScope.launch {
            _message.value = "Aguarde o código..."
            repeat(25) {
                delay(2000)
                configRepository.getWhatsAppStatus()?.let { wa ->
                    if (!wa.pairingCode.isNullOrBlank()) {
                        _pairingCode.value = wa.pairingCode
                        _whatsappStatus.value = "aguardando_codigo"
                        _message.value = "Código gerado! Digite no WhatsApp."
                        return@launch
                    }
                    if (wa.status in listOf("conectado", "isLogged", "inChat")) {
                        _isConnected.value = true
                        _message.value = ""
                        return@launch
                    }
                }
            }
            if (_pairingCode.value.isNullOrBlank() && !_isConnected.value) {
                _message.value = "Código não gerado. Toque em atualizar e tente novamente."
            }
        }
    }

    fun desconectar() {
        viewModelScope.launch {
            _message.value = "Desconectando..."
            configRepository.desconectarWhatsApp()
                .onSuccess {
                    _isConnected.value = false
                    _pairingCode.value = null
                    _whatsappStatus.value = "desconectado"
                    _message.value = "WhatsApp desconectado"
                }
                .onFailure {
                    _message.value = "Erro ao desconectar"
                }
        }
    }
}

@HiltViewModel
class ConfiguracoesViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
    private val serverConfig: ServerConfigStore,
    private val apiProvider: ApiProvider,
    private val socketService: SocketService,
    private val printerService: PrinterService,
    private val serverDiscovery: ServerDiscoveryService
) : ViewModel() {
    private val _config = MutableStateFlow<Map<String, String>>(emptyMap())
    val config = _config.asStateFlow()
    private val _serverUrl = MutableStateFlow("")
    val serverUrl = _serverUrl.asStateFlow()
    private val _message = MutableStateFlow("")
    val message = _message.asStateFlow()
    private val _printerMac = MutableStateFlow("")
    val printerMac = _printerMac.asStateFlow()
    private val _discovering = MutableStateFlow(false)
    val discovering = _discovering.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _serverUrl.value = serverConfig.getServerUrl()
            _config.value = configRepository.getConfiguracoes()
            _printerMac.value = serverConfig.getPrinterMac()
        }
    }

    fun buscarServidor() {
        viewModelScope.launch {
            _discovering.value = !serverDiscovery.isProductionMode()
            _message.value = if (serverDiscovery.isProductionMode()) {
                "Conectando ao servidor de produção..."
            } else {
                "Procurando servidor na rede..."
            }
            val url = serverDiscovery.resolveServerUrl(serverConfig.getServerUrl())
            if (serverDiscovery.probe(url) || serverDiscovery.isProductionMode()) {
                serverConfig.setServerUrl(url)
                apiProvider.resetApi()
                socketService.disconnect()
                socketService.connect()
                _serverUrl.value = url
                _message.value = if (serverDiscovery.isProductionMode()) {
                    "Conectado: $url"
                } else {
                    "Servidor encontrado: $url"
                }
            } else {
                _message.value = if (serverDiscovery.isProductionMode()) {
                    "Não foi possível conectar ao servidor. Verifique sua internet."
                } else {
                    "Servidor não encontrado. Verifique se o PC está ligado na mesma Wi-Fi."
                }
            }
            _discovering.value = false
        }
    }

    fun saveConfig(config: Map<String, String>) {
        viewModelScope.launch {
            configRepository.saveConfiguracoes(config).onSuccess {
                _config.value = it
                _message.value = "Configurações salvas!"
            }.onFailure {
                _message.value = "Erro ao salvar"
            }
        }
    }

    fun conectarImpressora(mac: String) {
        viewModelScope.launch {
            serverConfig.setPrinterMac(mac)
            _printerMac.value = mac
            if (printerService.connect(mac)) {
                _message.value = "Impressora conectada!"
            } else {
                _message.value = "Erro ao conectar impressora"
            }
        }
    }
}

@HiltViewModel
class ClientesViewModel @Inject constructor(
    private val clienteRepository: ClienteRepository
) : ViewModel() {
    val clientes = clienteRepository.getClientes()
    private val _message = MutableStateFlow("")
    val message = _message.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch { clienteRepository.refresh() }
    }

    fun excluirCliente(id: Long) {
        viewModelScope.launch {
            clienteRepository.deleteCliente(id)
                .onSuccess { _message.value = "Cliente excluído" }
                .onFailure { _message.value = it.message ?: "Erro ao excluir cliente" }
        }
    }

    fun clearMessage() {
        _message.value = ""
    }
}
