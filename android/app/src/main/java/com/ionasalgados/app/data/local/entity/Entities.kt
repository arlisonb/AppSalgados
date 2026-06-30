package com.ionasalgados.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "produtos")
data class ProdutoEntity(
    @PrimaryKey val id: Long,
    val nome: String,
    val categoriaId: Long,
    val categoriaNome: String?,
    val descricao: String?,
    val imagem: String?,
    val precoUnidade: Double,
    val precoCento: Double,
    val precoCentoCinquenta: Double,
    val precoDuzentos: Double,
    val precoTrezentos: Double,
    val precoQuinhentos: Double,
    val precoPersonalizado: Double,
    val tempoPreparo: Int,
    val ativo: Boolean,
    val observacaoPadrao: String?,
    val ordem: Int
)

@Entity(tableName = "categorias")
data class CategoriaEntity(
    @PrimaryKey val id: Long,
    val nome: String,
    val ordem: Int,
    val ativo: Boolean
)

@Entity(tableName = "pedidos")
data class PedidoEntity(
    @PrimaryKey val id: Long,
    val numero: Int,
    val clienteId: Long,
    val clienteNome: String?,
    val clienteTelefone: String?,
    val status: String,
    val valorItens: Double,
    val taxaEntrega: Double,
    val valorTotal: Double,
    val formaPagamento: String?,
    val troco: Double,
    val observacoes: String?,
    val endereco: String?,
    val origem: String,
    val tempoEstimado: Int?,
    val createdAt: String?,
    val sincronizado: Boolean = true
)

@Entity(tableName = "itens_pedido")
data class ItemPedidoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pedidoId: Long,
    val produtoId: Long,
    val nomeProduto: String,
    val quantidade: Int,
    val precoUnitario: Double,
    val subtotal: Double,
    val observacao: String?
)

@Entity(tableName = "clientes")
data class ClienteEntity(
    @PrimaryKey val id: Long,
    val nome: String,
    val telefone: String,
    val endereco: String?,
    val observacoes: String?,
    val qtdPedidos: Int,
    val valorTotal: Double,
    val ultimaCompra: String?
)

@Entity(tableName = "configuracoes")
data class ConfiguracaoEntity(
    @PrimaryKey val chave: String,
    val valor: String
)
