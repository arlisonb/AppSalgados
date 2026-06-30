package com.ionasalgados.app.domain.model

data class Categoria(
    val id: Long = 0,
    val nome: String,
    val ordem: Int = 0,
    val ativo: Boolean = true
)

data class Produto(
    val id: Long = 0,
    val nome: String,
    val categoriaId: Long,
    val categoriaNome: String? = null,
    val descricao: String? = null,
    val imagem: String? = null,
    val precoUnidade: Double = 0.0,
    val precoCento: Double = 0.0,
    val precoCentoCinquenta: Double = 0.0,
    val precoDuzentos: Double = 0.0,
    val precoTrezentos: Double = 0.0,
    val precoQuinhentos: Double = 0.0,
    val precoPersonalizado: Double = 0.0,
    val tempoPreparo: Int = 30,
    val ativo: Boolean = true,
    val observacaoPadrao: String? = null,
    val ordem: Int = 0
)

data class Cliente(
    val id: Long = 0,
    val nome: String,
    val telefone: String,
    val endereco: String? = null,
    val observacoes: String? = null,
    val qtdPedidos: Int = 0,
    val pedidosAtivos: Int = 0,
    val valorTotal: Double = 0.0,
    val ultimaCompra: String? = null
)

data class Pedido(
    val id: Long = 0,
    val numero: Int,
    val clienteId: Long,
    val clienteNome: String? = null,
    val clienteTelefone: String? = null,
    val status: StatusPedido,
    val valorItens: Double = 0.0,
    val taxaEntrega: Double = 0.0,
    val valorTotal: Double = 0.0,
    val formaPagamento: String? = null,
    val troco: Double = 0.0,
    val observacoes: String? = null,
    val endereco: String? = null,
    val origem: String = "app",
    val tempoEstimado: Int? = null,
    val createdAt: String? = null,
    val itens: List<ItemPedido> = emptyList()
)

data class ItemPedido(
    val id: Long = 0,
    val pedidoId: Long = 0,
    val produtoId: Long,
    val nomeProduto: String,
    val quantidade: Int,
    val precoUnitario: Double,
    val subtotal: Double,
    val observacao: String? = null
)

enum class StatusPedido(val label: String, val cor: Long) {
    NOVO("Novo", 0xFF2196F3),
    CONFIRMADO("Confirmado", 0xFF03A9F4),
    PREPARANDO("Preparando", 0xFFFF9800),
    PRONTO("Pronto", 0xFF4CAF50),
    SAIU_ENTREGA("Saiu p/ Entrega", 0xFF9C27B0),
    FINALIZADO("Finalizado", 0xFF607D8B),
    CANCELADO("Cancelado", 0xFFF44336);

    companion object {
        fun fromString(value: String): StatusPedido = entries.find {
            it.name.equals(value.replace(" ", "_").uppercase(), ignoreCase = true) ||
            it.name.replace("_", "").equals(value.replace(" ", "").replace("_", ""), ignoreCase = true)
        } ?: NOVO
    }
}

data class Dashboard(
    val pedidosHoje: Int = 0,
    val pendentes: Int = 0,
    val finalizados: Int = 0,
    val cancelados: Int = 0,
    val emProducao: Int = 0,
    val saiuEntrega: Int = 0,
    val valorHoje: Double = 0.0,
    val valorMes: Double = 0.0,
    val clientesAtendidos: Int = 0,
    val produtoMaisVendido: String? = null,
    val caixaAtual: Double = 0.0
)

data class Configuracao(
    val nomeEmpresa: String = "Iona Salgados",
    val telefone: String = "",
    val whatsapp: String = "",
    val endereco: String = "",
    val pix: String = "",
    val taxaEntrega: Double = 0.0,
    val tempoMedio: Int = 60,
    val horarioFuncionamento: String = "",
    val mensagemInicial: String = "",
    val mensagemFinal: String = ""
)
