package com.ionasalgados.app.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.ionasalgados.app.domain.model.*

data class ProdutoDto(
    val id: Long = 0,
    val nome: String,
    @SerializedName("categoria_id") val categoriaId: Long,
    @SerializedName("categoria_nome") val categoriaNome: String? = null,
    val descricao: String? = null,
    val imagem: String? = null,
    @SerializedName("preco_unidade") val precoUnidade: Double = 0.0,
    @SerializedName("preco_cento") val precoCento: Double = 0.0,
    @SerializedName("preco_cento_cinquenta") val precoCentoCinquenta: Double = 0.0,
    @SerializedName("preco_duzentos") val precoDuzentos: Double = 0.0,
    @SerializedName("preco_trezentos") val precoTrezentos: Double = 0.0,
    @SerializedName("preco_quinhentos") val precoQuinhentos: Double = 0.0,
    @SerializedName("preco_personalizado") val precoPersonalizado: Double = 0.0,
    @SerializedName("tempo_preparo") val tempoPreparo: Int = 30,
    val ativo: Boolean = true,
    @SerializedName("observacao_padrao") val observacaoPadrao: String? = null,
    val ordem: Int = 0
) {
    fun toDomain() = Produto(
        id, nome, categoriaId, categoriaNome, descricao, imagem,
        precoUnidade, precoCento, precoCentoCinquenta, precoDuzentos,
        precoTrezentos, precoQuinhentos, precoPersonalizado, tempoPreparo,
        ativo, observacaoPadrao, ordem
    )

    fun toEntity() = com.ionasalgados.app.data.local.entity.ProdutoEntity(
        id, nome, categoriaId, categoriaNome, descricao, imagem,
        precoUnidade, precoCento, precoCentoCinquenta, precoDuzentos,
        precoTrezentos, precoQuinhentos, precoPersonalizado, tempoPreparo,
        ativo, observacaoPadrao, ordem
    )
}

data class CategoriaDto(
    val id: Long = 0,
    val nome: String,
    val ordem: Int = 0,
    val ativo: Boolean = true
) {
    fun toDomain() = Categoria(id, nome, ordem, ativo)
    fun toEntity() = com.ionasalgados.app.data.local.entity.CategoriaEntity(id, nome, ordem, ativo)
}

data class ClienteDto(
    val id: Long = 0,
    val nome: String,
    val telefone: String,
    val endereco: String? = null,
    val observacoes: String? = null,
    @SerializedName("qtd_pedidos") val qtdPedidos: Int = 0,
    @SerializedName("pedidos_ativos") val pedidosAtivos: Int = 0,
    @SerializedName("valor_total") val valorTotal: Double = 0.0,
    @SerializedName("ultima_compra") val ultimaCompra: String? = null
) {
    fun toDomain() = Cliente(id, nome, telefone, endereco, observacoes, qtdPedidos, pedidosAtivos, valorTotal, ultimaCompra)
}

data class PedidoDto(
    val id: Long = 0,
    val numero: Int,
    @SerializedName("cliente_id") val clienteId: Long,
    @SerializedName("cliente_nome") val clienteNome: String? = null,
    @SerializedName("cliente_telefone") val clienteTelefone: String? = null,
    val status: String,
    @SerializedName("valor_itens") val valorItens: Double = 0.0,
    @SerializedName("taxa_entrega") val taxaEntrega: Double = 0.0,
    @SerializedName("valor_total") val valorTotal: Double = 0.0,
    @SerializedName("forma_pagamento") val formaPagamento: String? = null,
    val troco: Double = 0.0,
    val observacoes: String? = null,
    val endereco: String? = null,
    val origem: String = "app",
    @SerializedName("tempo_estimado") val tempoEstimado: Int? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    val itens: List<ItemPedidoDto>? = null
) {
    fun toDomain() = Pedido(
        id, numero, clienteId, clienteNome, clienteTelefone,
        StatusPedido.fromString(status), valorItens, taxaEntrega, valorTotal,
        formaPagamento, troco, observacoes, endereco, origem, tempoEstimado,
        createdAt, itens?.map { it.toDomain() } ?: emptyList()
    )
}

data class ItemPedidoDto(
    val id: Long = 0,
    @SerializedName("pedido_id") val pedidoId: Long = 0,
    @SerializedName("produto_id") val produtoId: Long,
    @SerializedName("nome_produto") val nomeProduto: String,
    val quantidade: Int,
    @SerializedName("preco_unitario") val precoUnitario: Double,
    val subtotal: Double,
    val observacao: String? = null
) {
    fun toDomain() = ItemPedido(id, pedidoId, produtoId, nomeProduto, quantidade, precoUnitario, subtotal, observacao)
}

data class DashboardDto(
    @SerializedName("pedidos_hoje") val pedidosHoje: Int = 0,
    val pendentes: Int = 0,
    val finalizados: Int = 0,
    val cancelados: Int = 0,
    @SerializedName("em_producao") val emProducao: Int = 0,
    @SerializedName("saiu_entrega") val saiuEntrega: Int = 0,
    @SerializedName("valor_hoje") val valorHoje: Double = 0.0,
    @SerializedName("valor_mes") val valorMes: Double = 0.0,
    @SerializedName("clientes_atendidos") val clientesAtendidos: Int = 0,
    @SerializedName("produto_mais_vendido") val produtoMaisVendido: String? = null,
    @SerializedName("caixa_atual") val caixaAtual: Double = 0.0
) {
    fun toDomain() = Dashboard(
        pedidosHoje, pendentes, finalizados, cancelados, emProducao, saiuEntrega,
        valorHoje, valorMes, clientesAtendidos, produtoMaisVendido, caixaAtual
    )
}

data class ProducaoDto(
    @SerializedName("porProduto") val porProduto: List<ProducaoItemDto>? = null,
    @SerializedName("porHorario") val porHorario: List<ProducaoItemDto>? = null,
    @SerializedName("porCliente") val porCliente: List<ProducaoItemDto>? = null
)

data class ProducaoItemDto(
    @SerializedName("nome_produto") val nomeProduto: String? = null,
    @SerializedName("quantidade_total") val quantidadeTotal: Int? = null,
    @SerializedName("cliente_nome") val clienteNome: String? = null,
    val numero: Int? = null,
    val status: String? = null,
    val quantidade: Int? = null,
    val horario: String? = null
)

data class FinanceiroResumoDto(
    @SerializedName("caixa_atual") val caixaAtual: Double = 0.0,
    @SerializedName("caixa_aberto") val caixaAberto: Boolean = false,
    @SerializedName("faturamento_hoje") val faturamentoHoje: Double = 0.0,
    @SerializedName("faturamento_mes") val faturamentoMes: Double = 0.0,
    @SerializedName("pedidos_hoje") val pedidosHoje: Int = 0,
    @SerializedName("pix_recebido") val pixRecebido: Double = 0.0,
    @SerializedName("dinheiro_recebido") val dinheiroRecebido: Double = 0.0
)

data class CaixaDto(
    val aberto: Boolean = false,
    val id: Long? = null,
    @SerializedName("valor_inicial") val valorInicial: Double = 0.0,
    @SerializedName("valor_final") val valorFinal: Double = 0.0
)

data class StatusDto(
    val servidor: String,
    val whatsapp: Map<String, String>?,
    val timestamp: String
)
