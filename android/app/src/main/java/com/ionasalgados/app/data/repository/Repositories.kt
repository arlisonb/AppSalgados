package com.ionasalgados.app.data.repository

import com.ionasalgados.app.data.local.ServerConfigStore
import com.ionasalgados.app.data.local.dao.*
import com.ionasalgados.app.data.remote.ApiProvider
import com.ionasalgados.app.data.remote.dto.*
import com.ionasalgados.app.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PedidoRepository @Inject constructor(
    private val apiProvider: ApiProvider,
    private val pedidoDao: PedidoDao,
    private val itemPedidoDao: ItemPedidoDao
) {
    fun getPedidosAtivos(): Flow<List<Pedido>> =
        pedidoDao.getAtivos().map { list -> list.map { it.toDomain() } }

    fun getPedidosHoje(): Flow<List<Pedido>> =
        pedidoDao.getHoje().map { list -> list.map { it.toDomain() } }

    suspend fun getPedido(id: Long): Pedido? {
        return try {
            val response = apiProvider.getApi().getPedido(id)
            if (response.isSuccessful) {
                response.body()?.let { dto ->
                    cachePedido(dto)
                    dto.toDomain()
                }
            } else {
                pedidoDao.getById(id)?.toDomain()
            }
        } catch (e: Exception) {
            pedidoDao.getById(id)?.toDomain()
        }
    }

    suspend fun refreshPedidos() {
        try {
            val response = apiProvider.getApi().getPedidos()
            if (response.isSuccessful) {
                response.body()?.forEach { dto -> cachePedido(dto) }
            }
        } catch (_: Exception) { }
    }

    suspend fun cachePedido(pedido: Pedido) {
        pedidoDao.insert(pedido.toEntity())
        if (pedido.itens.isNotEmpty()) {
            itemPedidoDao.insertAll(pedido.itens.map { it.toEntity(pedido.id) })
        }
    }

    suspend fun updateStatus(id: Long, status: StatusPedido): Result<Pedido> {
        val statusApi = when (status) {
            StatusPedido.SAIU_ENTREGA -> "saiu_entrega"
            else -> status.name.lowercase()
        }
        return try {
            val response = apiProvider.getApi().updatePedidoStatus(id, mapOf("status" to statusApi))
            if (response.isSuccessful) {
                response.body()?.let { dto ->
                    cachePedido(dto)
                    Result.success(dto.toDomain())
                } ?: Result.failure(Exception("Resposta vazia"))
            } else {
                Result.failure(Exception("Erro ao atualizar status"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun cachePedido(dto: PedidoDto) {
        pedidoDao.insert(dto.toEntity())
        dto.itens?.let { itens ->
            itemPedidoDao.insertAll(itens.map { item -> item.toEntity(dto.id) })
        }
    }

    private fun com.ionasalgados.app.data.local.entity.PedidoEntity.toDomain() = Pedido(
        id, numero, clienteId, clienteNome, clienteTelefone,
        StatusPedido.fromString(status), valorItens, taxaEntrega, valorTotal,
        formaPagamento, troco, observacoes, endereco, origem, tempoEstimado, createdAt
    )

    private fun Pedido.toEntity() = com.ionasalgados.app.data.local.entity.PedidoEntity(
        id, numero, clienteId, clienteNome, clienteTelefone, status.name.lowercase(),
        valorItens, taxaEntrega, valorTotal, formaPagamento, troco,
        observacoes, endereco, origem, tempoEstimado, createdAt
    )

    private fun ItemPedido.toEntity(pedidoId: Long) = com.ionasalgados.app.data.local.entity.ItemPedidoEntity(
        id, pedidoId, produtoId, nomeProduto, quantidade, precoUnitario, subtotal, observacao
    )

    private fun PedidoDto.toEntity() = com.ionasalgados.app.data.local.entity.PedidoEntity(
        id, numero, clienteId, clienteNome, clienteTelefone, status,
        valorItens, taxaEntrega, valorTotal, formaPagamento, troco,
        observacoes, endereco, origem, tempoEstimado, createdAt
    )

    private fun PedidoDto.toDomain() = Pedido(
        id, numero, clienteId, clienteNome, clienteTelefone,
        StatusPedido.fromString(status), valorItens, taxaEntrega, valorTotal,
        formaPagamento, troco, observacoes, endereco, origem, tempoEstimado,
        createdAt, itens?.map { it.toDomain() } ?: emptyList()
    )

    private fun ItemPedidoDto.toEntity(pedidoId: Long) = com.ionasalgados.app.data.local.entity.ItemPedidoEntity(
        id, pedidoId, produtoId, nomeProduto, quantidade, precoUnitario, subtotal, observacao
    )

    private fun com.ionasalgados.app.data.local.entity.ItemPedidoEntity.toDomain() = ItemPedido(
        id, pedidoId, produtoId, nomeProduto, quantidade, precoUnitario, subtotal, observacao
    )
}

@Singleton
class ProdutoRepository @Inject constructor(
    private val apiProvider: ApiProvider,
    private val produtoDao: ProdutoDao,
    private val categoriaDao: CategoriaDao,
    private val configRepository: ConfigRepository,
    private val serverDiscovery: com.ionasalgados.app.data.remote.ServerDiscoveryService,
    private val serverConfig: ServerConfigStore
) {
    fun getProdutos(): Flow<List<Produto>> =
        produtoDao.getAll().map { list -> list.map { it.toDomain() } }

    fun getCategorias(): Flow<List<Categoria>> =
        categoriaDao.getAll().map { list -> list.map { it.toDomain() } }

    suspend fun refresh() {
        try {
            val api = apiProvider.getApi()
            api.getCategorias().body()?.let { categorias ->
                categoriaDao.deleteAll()
                categoriaDao.insertAll(categorias.map { it.toEntity() })
            }
            api.getProdutos(true).body()?.let { produtos ->
                produtoDao.deleteAll()
                produtoDao.insertAll(produtos.map { it.toEntity() })
            }
        } catch (_: Exception) { }
    }

    suspend fun createProduto(
        nome: String,
        categoriaNome: String,
        precoUnidade: Double,
        precoCento: Double
    ): Result<Produto> = try {
        val dto = ProdutoDto(
            nome = nome.trim(),
            categoriaId = 0,
            categoriaNome = categoriaNome.trim(),
            precoUnidade = precoUnidade,
            precoCento = precoCento,
            ativo = true
        )
        val response = apiProvider.getApi().createProduto(dto)
        if (response.isSuccessful) {
            refresh()
            val produto = response.body()?.toDomain()
            if (produto != null) Result.success(produto)
            else Result.success(Produto(0, nome, 0, categoriaNome, null, null, precoUnidade, precoCento, 0.0, 0.0, 0.0, 0.0, 0.0, 30, true, null, 0))
        } else Result.failure(Exception("Erro ${response.code()}: ${response.errorBody()?.string()}"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun ensureServerConnection(): Boolean {
        if (configRepository.testConnection()) return true
        serverDiscovery.discover()?.let { url ->
            serverConfig.setServerUrl(url)
            apiProvider.resetApi()
        }
        return configRepository.testConnection()
    }

    suspend fun deleteProduto(id: Long): Result<Unit> = try {
        val response = apiProvider.getApi().deleteProduto(id)
        if (response.isSuccessful) {
            refresh()
            Result.success(Unit)
        } else Result.failure(Exception("Erro ao excluir produto"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun com.ionasalgados.app.data.local.entity.ProdutoEntity.toDomain() = Produto(
        id, nome, categoriaId, categoriaNome, descricao, imagem,
        precoUnidade, precoCento, precoCentoCinquenta, precoDuzentos,
        precoTrezentos, precoQuinhentos, precoPersonalizado, tempoPreparo,
        ativo, observacaoPadrao, ordem
    )

    private fun com.ionasalgados.app.data.local.entity.CategoriaEntity.toDomain() = Categoria(id, nome, ordem, ativo)
}

@Singleton
class ClienteRepository @Inject constructor(
    private val apiProvider: ApiProvider,
    private val clienteDao: ClienteDao
) {
    fun getClientes(): Flow<List<Cliente>> =
        clienteDao.getAll().map { list -> list.map { it.toDomain() } }

    suspend fun refresh() {
        try {
            val response = apiProvider.getApi().getClientes()
            if (response.isSuccessful) {
                clienteDao.deleteAll()
                response.body()?.let { list ->
                    clienteDao.insertAll(list.map { it.toEntity() })
                }
            }
        } catch (_: Exception) { }
    }

    suspend fun deleteCliente(id: Long): Result<Unit> = try {
        val response = apiProvider.getApi().deleteCliente(id)
        if (response.isSuccessful) {
            clienteDao.deleteById(id)
            Result.success(Unit)
        } else {
            Result.failure(Exception("Cliente possui pedidos em andamento"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun ClienteDto.toEntity() = com.ionasalgados.app.data.local.entity.ClienteEntity(
        id, nome, telefone, endereco, observacoes, qtdPedidos, valorTotal, ultimaCompra
    )

    private fun com.ionasalgados.app.data.local.entity.ClienteEntity.toDomain() = Cliente(
        id, nome, telefone, endereco, observacoes, qtdPedidos, 0, valorTotal, ultimaCompra
    )
}

@Singleton
class DashboardRepository @Inject constructor(
    private val apiProvider: ApiProvider
) {
    suspend fun getDashboard(): Dashboard {
        return try {
            val response = apiProvider.getApi().getDashboard()
            if (response.isSuccessful) response.body()?.toDomain() ?: Dashboard()
            else Dashboard()
        } catch (_: Exception) {
            Dashboard()
        }
    }

    suspend fun getProducao(): ProducaoDto? = try {
        apiProvider.getApi().getProducao(null).body()
    } catch (_: Exception) {
        null
    }

    suspend fun getRelatorio(periodo: String): RelatorioDto? = try {
        apiProvider.getApi().getRelatorio(periodo, null, null).body()
    } catch (_: Exception) {
        null
    }
}

@Singleton
class FinanceiroRepository @Inject constructor(
    private val apiProvider: ApiProvider
) {
    suspend fun getResumo() = try {
        apiProvider.getApi().getFinanceiroResumo().body()
    } catch (_: Exception) {
        null
    }

    suspend fun getCaixa() = try {
        apiProvider.getApi().getCaixa().body()
    } catch (_: Exception) {
        null
    }

    suspend fun abrirCaixa(valorInicial: Double): Result<CaixaDto> = try {
        val response = apiProvider.getApi().abrirCaixa(mapOf("valor_inicial" to valorInicial))
        if (response.isSuccessful) Result.success(response.body()!!)
        else Result.failure(Exception("Erro ao abrir caixa"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun fecharCaixa(obs: String?) = try {
        val response = apiProvider.getApi().fecharCaixa(mapOf("observacoes" to obs))
        if (response.isSuccessful) Result.success(response.body()!!)
        else Result.failure(Exception("Erro ao fechar caixa"))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

@Singleton
class ConfigRepository @Inject constructor(
    private val apiProvider: ApiProvider,
    private val serverConfig: ServerConfigStore
) {
    suspend fun getConfiguracoes(): Map<String, String> = try {
        apiProvider.getApi().getConfiguracoes().body() ?: emptyMap()
    } catch (_: Exception) {
        emptyMap()
    }

    suspend fun saveConfiguracoes(config: Map<String, String>): Result<Map<String, String>> = try {
        val response = apiProvider.getApi().updateConfiguracoes(config)
        if (response.isSuccessful) {
            config["whatsapp"]?.let { serverConfig.setWhatsAppPhone(it) }
            Result.success(response.body() ?: config)
        } else Result.failure(Exception("Erro ao salvar"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getWhatsAppStatus(): WhatsAppStatusDto? = try {
        apiProvider.getApi().getWhatsAppStatus().body()
    } catch (_: Exception) {
        null
    }

    suspend fun reconectarWhatsApp(telefone: String) = try {
        val response = apiProvider.getApi().reconectarWhatsApp(mapOf("telefone" to telefone))
        if (response.isSuccessful) Result.success(response.body()!!)
        else Result.failure(Exception("Erro ao reconectar"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun desconectarWhatsApp() = try {
        val response = apiProvider.getApi().desconectarWhatsApp()
        if (response.isSuccessful) Result.success(response.body()!!)
        else Result.failure(Exception("Erro ao desconectar"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun testConnection(): Boolean = try {
        apiProvider.getApi().getStatus().isSuccessful
    } catch (_: Exception) {
        false
    }
}

data class RelatorioDto(
    val faturamento: Double = 0.0,
    val lucro: Double = 0.0,
    val qtd_pedidos: Int = 0,
    val pedidos_cancelados: Int = 0,
    val ticket_medio: Double = 0.0,
    val despesas: Double = 0.0,
    val saldo: Double = 0.0,
    val produtos_mais_vendidos: List<ProdutoVendidoDto>? = null
)

data class ProdutoVendidoDto(val nome_produto: String?, val quantidade: Int?, val valor: Double?)

data class WhatsAppStatusDto(
    val status: String?,
    val pairingCode: String?,
    val phoneNumber: String?,
    val session: String?
)
