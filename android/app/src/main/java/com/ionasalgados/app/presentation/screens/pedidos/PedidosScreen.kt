package com.ionasalgados.app.presentation.screens.pedidos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ionasalgados.app.domain.model.Pedido
import com.ionasalgados.app.domain.model.StatusPedido
import com.ionasalgados.app.presentation.theme.LaranjaIona
import com.ionasalgados.app.presentation.viewmodel.PedidosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PedidosScreen(
    onBack: () -> Unit,
    onPedidoClick: (Long) -> Unit,
    viewModel: PedidosViewModel = hiltViewModel()
) {
    val pedidos by viewModel.pedidos.collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pedidos", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Atualizar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LaranjaIona, titleContentColor = Color.White)
            )
        }
    ) { padding ->
        if (pedidos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Nenhum pedido ativo", style = MaterialTheme.typography.headlineSmall)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pedidos, key = { it.id }) { pedido ->
                    PedidoCard(pedido = pedido, onClick = { onPedidoClick(pedido.id) })
                }
            }
        }
    }
}

@Composable
fun PedidoCard(pedido: Pedido, onClick: () -> Unit) {
    val statusColor = Color(pedido.status.cor)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp, 48.dp)
                    .background(statusColor, RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "#${pedido.numero} - ${pedido.clienteNome ?: "Cliente"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(pedido.clienteTelefone ?: "", style = MaterialTheme.typography.bodyMedium)
                Text(
                    pedido.createdAt?.take(16) ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "R$ %.2f".format(pedido.valorTotal),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LaranjaIona
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        pedido.status.label,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = statusColor
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PedidoDetalheScreen(
    pedidoId: Long,
    onBack: () -> Unit,
    viewModel: com.ionasalgados.app.presentation.viewmodel.PedidoDetalheViewModel = hiltViewModel()
) {
    val pedido by viewModel.pedido.collectAsState()
    val message by viewModel.message.collectAsState()

    LaunchedEffect(pedidoId) { viewModel.loadPedido(pedidoId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pedido #${pedido?.numero ?: ""}", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LaranjaIona, titleContentColor = Color.White)
            )
        }
    ) { padding ->
        pedido?.let { p ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (message.isNotBlank()) {
                    item {
                        Text(
                            message,
                            color = if (message.contains("Erro", ignoreCase = true) || message.contains("não conectada", ignoreCase = true)) {
                                Color(0xFFF44336)
                            } else {
                                Color(0xFF4CAF50)
                            },
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                item {
                    InfoCard("Cliente", p.clienteNome ?: "")
                    InfoCard("Telefone", p.clienteTelefone ?: "")
                    InfoCard("Endereço", p.endereco ?: "")
                    InfoCard("Pagamento", p.formaPagamento ?: "")
                    if (p.troco > 0) InfoCard("Troco", "R$ %.2f".format(p.troco))
                    InfoCard("Total", "R$ %.2f".format(p.valorTotal))
                }
                item {
                    Text("Itens", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                items(p.itens) { item ->
                    Text("${item.quantidade}x ${item.nomeProduto} - R$ %.2f".format(item.subtotal))
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    when (p.status) {
                        StatusPedido.SAIU_ENTREGA -> {
                            Text(
                                "🛵 Aguardando confirmação do cliente no WhatsApp",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.reimprimir() },
                                    modifier = Modifier.weight(1f).height(56.dp)
                                ) {
                                    Text("Imprimir", style = MaterialTheme.typography.titleMedium)
                                }
                                Button(
                                    onClick = { viewModel.updateStatus(p.id, StatusPedido.FINALIZADO) },
                                    modifier = Modifier.weight(1f).height(56.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                                ) {
                                    Text("Marcar como entregue", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                        StatusPedido.FINALIZADO -> {
                            Text(
                                "✅ Entregue e confirmado pelo cliente",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { viewModel.reimprimir() },
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                            ) {
                                Text("Imprimir", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        StatusPedido.CANCELADO -> {
                            Text(p.status.label, style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                        }
                        else -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.reimprimir() },
                                    modifier = Modifier.weight(1f).height(56.dp)
                                ) {
                                    Text("Imprimir", style = MaterialTheme.typography.titleMedium)
                                }
                                Button(
                                    onClick = { viewModel.updateStatus(p.id, StatusPedido.SAIU_ENTREGA) },
                                    modifier = Modifier.weight(1f).height(56.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = LaranjaIona)
                                ) {
                                    Text("Saiu p/ Entrega", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(label: String, valor: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontWeight = FontWeight.Medium)
        Text(valor)
    }
}
