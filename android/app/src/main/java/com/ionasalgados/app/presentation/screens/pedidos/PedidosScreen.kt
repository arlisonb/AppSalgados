package com.ionasalgados.app.presentation.screens.pedidos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.ionasalgados.app.presentation.components.*
import com.ionasalgados.app.presentation.theme.LaranjaIona
import com.ionasalgados.app.presentation.theme.MarromSuave
import com.ionasalgados.app.presentation.viewmodel.PedidosViewModel

@Composable
fun PedidosScreen(
    onBack: () -> Unit,
    onPedidoClick: (Long) -> Unit,
    viewModel: PedidosViewModel = hiltViewModel()
) {
    val pedidos by viewModel.pedidos.collectAsState(initial = emptyList())

    LaunchedEffect(Unit) { viewModel.refresh() }

    IonaScaffold(
        title = "Pedidos",
        subtitle = "Em andamento",
        onBack = onBack,
        actions = {
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(Icons.Default.Refresh, "Atualizar", tint = Color.White)
            }
        }
    ) { padding ->
        if (pedidos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                IonaEmptyState("Nenhum pedido ativo", "Novos pedidos aparecem aqui 🥟")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }
                items(pedidos, key = { it.id }) { pedido ->
                    PedidoCard(pedido = pedido, onClick = { onPedidoClick(pedido.id) })
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
fun PedidoCard(pedido: Pedido, onClick: () -> Unit) {
    val statusColor = Color(pedido.status.cor)

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = IonaCardShape,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
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
                    "#${pedido.numero} — ${pedido.clienteNome ?: "Cliente"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MarromSuave
                )
                Text(pedido.clienteTelefone ?: "", style = MaterialTheme.typography.bodyMedium, color = MarromSuave.copy(alpha = 0.7f))
                Text(pedido.createdAt?.take(16) ?: "", style = MaterialTheme.typography.bodySmall, color = MarromSuave.copy(alpha = 0.5f))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "R$ %.2f".format(pedido.valorTotal),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LaranjaIona
                )
                Surface(shape = RoundedCornerShape(10.dp), color = statusColor.copy(alpha = 0.15f)) {
                    Text(
                        pedido.status.label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun PedidoDetalheScreen(
    pedidoId: Long,
    onBack: () -> Unit,
    viewModel: com.ionasalgados.app.presentation.viewmodel.PedidoDetalheViewModel = hiltViewModel()
) {
    val pedido by viewModel.pedido.collectAsState()
    val message by viewModel.message.collectAsState()

    LaunchedEffect(pedidoId) { viewModel.loadPedido(pedidoId) }

    IonaScaffold(
        title = "Pedido #${pedido?.numero ?: ""}",
        onBack = onBack
    ) { padding ->
        pedido?.let { p ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                if (message.isNotBlank()) {
                    item {
                        IonaMessageBanner(
                            message = message,
                            success = !message.contains("Erro", ignoreCase = true) && !message.contains("não", ignoreCase = true)
                        )
                    }
                }

                item {
                    IonaSurfaceCard {
                        InfoRow("Cliente", p.clienteNome ?: "")
                        InfoRow("Telefone", p.clienteTelefone ?: "")
                        InfoRow("Endereço", p.endereco ?: "—")
                        InfoRow("Pagamento", p.formaPagamento ?: "—")
                        if (p.troco > 0) InfoRow("Troco", "R$ %.2f".format(p.troco))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MarromSuave.copy(alpha = 0.1f))
                        InfoRow("Total", "R$ %.2f".format(p.valorTotal), bold = true)
                    }
                }

                item { IonaSectionTitle("Itens") }

                items(p.itens) { item ->
                    IonaSurfaceCard(elevation = 1.dp) {
                        Text(
                            "${item.quantidade}x ${item.nomeProduto}",
                            fontWeight = FontWeight.Medium,
                            color = MarromSuave
                        )
                        Text("R$ %.2f".format(item.subtotal), color = LaranjaIona, fontWeight = FontWeight.SemiBold)
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    when (p.status) {
                        StatusPedido.SAIU_ENTREGA -> {
                            Text(
                                "🛵 Aguardando confirmação do cliente no WhatsApp",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MarromSuave.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.reimprimir() },
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) { Text("Imprimir") }
                                Button(
                                    onClick = { viewModel.updateStatus(p.id, StatusPedido.FINALIZADO) },
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                                ) { Text("Entregue") }
                            }
                        }
                        StatusPedido.FINALIZADO -> {
                            Text("✅ Entregue e confirmado", color = MarromSuave.copy(alpha = 0.7f))
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { viewModel.reimprimir() },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) { Text("Imprimir") }
                        }
                        StatusPedido.CANCELADO -> {
                            Text(p.status.label, color = MarromSuave.copy(alpha = 0.7f))
                        }
                        else -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.reimprimir() },
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) { Text("Imprimir") }
                                Button(
                                    onClick = { viewModel.updateStatus(p.id, StatusPedido.SAIU_ENTREGA) },
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = LaranjaIona)
                                ) { Text("Saiu p/ Entrega") }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, valor: String, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MarromSuave.copy(alpha = 0.7f))
        Text(valor, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = MarromSuave)
    }
}
