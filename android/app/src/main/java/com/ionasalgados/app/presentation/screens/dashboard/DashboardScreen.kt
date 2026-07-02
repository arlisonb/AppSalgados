package com.ionasalgados.app.presentation.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ionasalgados.app.domain.model.Dashboard
import com.ionasalgados.app.domain.model.Pedido
import com.ionasalgados.app.presentation.components.*
import com.ionasalgados.app.presentation.theme.LaranjaIona
import com.ionasalgados.app.presentation.theme.MarromSuave
import com.ionasalgados.app.presentation.viewmodel.DashboardViewModel

data class DashboardCard(
    val titulo: String,
    val valor: String,
    val icone: ImageVector,
    val clicavel: Boolean = false
)

@Composable
fun DashboardScreen(
    onBack: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val dashboard by viewModel.dashboard.collectAsState()
    val pedidos by viewModel.pedidos.collectAsState(initial = emptyList())
    val message by viewModel.message.collectAsState()
    var mostrarPedidosHoje by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadDashboard() }
    LaunchedEffect(message) {
        if (message.isNotBlank()) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearMessage()
        }
    }

    IonaScaffold(
        title = "Painel",
        subtitle = "Resumo do dia",
        onBack = onBack,
        actions = {
            IconButton(onClick = { viewModel.loadDashboard() }) {
                Icon(Icons.Default.Refresh, "Atualizar", tint = Color.White)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            if (message.isNotBlank()) {
                item {
                    IonaMessageBanner(
                        message = message,
                        success = !message.contains("não", ignoreCase = true)
                    )
                }
            }

            item {
                DashboardGrid(
                    dashboard = dashboard,
                    pedidosHojeSelecionado = mostrarPedidosHoje,
                    onPedidosHojeClick = { mostrarPedidosHoje = !mostrarPedidosHoje }
                )
            }

            if (mostrarPedidosHoje) {
                item { IonaSectionTitle("Pedidos de hoje", modifier = Modifier.padding(top = 4.dp)) }
                if (pedidos.isEmpty()) {
                    item {
                        Text(
                            "Nenhum pedido hoje",
                            color = MarromSuave.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                } else {
                    items(pedidos, key = { it.id }) { pedido ->
                        PedidoHojeCard(pedido = pedido, onImprimir = { viewModel.imprimirPedido(pedido.id) })
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun PedidoHojeCard(pedido: Pedido, onImprimir: () -> Unit) {
    IonaSurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "#${pedido.numero} — ${pedido.clienteNome ?: "Cliente"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MarromSuave
                )
                Text(pedido.status.label, style = MaterialTheme.typography.bodySmall, color = MarromSuave.copy(alpha = 0.6f))
                Text(pedido.createdAt?.take(16) ?: "", style = MaterialTheme.typography.bodySmall, color = MarromSuave.copy(alpha = 0.5f))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "R$ %.2f".format(pedido.valorTotal),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LaranjaIona
                )
                IconButton(onClick = onImprimir) {
                    Icon(Icons.Default.Print, "Imprimir", tint = LaranjaIona)
                }
            }
        }
    }
}

@Composable
private fun DashboardGrid(
    dashboard: Dashboard,
    pedidosHojeSelecionado: Boolean,
    onPedidosHojeClick: () -> Unit
) {
    val cards = listOf(
        DashboardCard("Pedidos Hoje", dashboard.pedidosHoje.toString(), Icons.Default.ShoppingBag, clicavel = true),
        DashboardCard("Faturamento Mensal", "R$ %.0f".format(dashboard.valorMes), Icons.Default.CalendarMonth),
        DashboardCard("Faturamento Diário", "R$ %.0f".format(dashboard.valorHoje), Icons.Default.AttachMoney),
        DashboardCard("Finalizados", dashboard.finalizados.toString(), Icons.Default.CheckCircle),
        DashboardCard("Clientes", dashboard.clientesAtendidos.toString(), Icons.Default.People),
    )

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        cards.chunked(2).forEach { rowCards ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                rowCards.forEach { card ->
                    Box(modifier = Modifier.weight(1f)) {
                        DashboardCardItem(
                            card = card,
                            pedidosHojeSelecionado = pedidosHojeSelecionado,
                            onPedidosHojeClick = onPedidosHojeClick
                        )
                    }
                }
                if (rowCards.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DashboardCardItem(
    card: DashboardCard,
    pedidosHojeSelecionado: Boolean,
    onPedidosHojeClick: () -> Unit
) {
    val selecionado = card.clicavel && pedidosHojeSelecionado
    val border = if (selecionado) androidx.compose.foundation.BorderStroke(2.dp, LaranjaIona) else null

    if (card.clicavel) {
        Card(
            onClick = onPedidosHojeClick,
            modifier = Modifier.fillMaxWidth().height(108.dp),
            shape = IonaCardShape,
            colors = CardDefaults.cardColors(
                containerColor = if (selecionado) LaranjaIona.copy(alpha = 0.12f) else Color.White
            ),
            elevation = CardDefaults.cardElevation(if (selecionado) 6.dp else 2.dp),
            border = border
        ) { DashboardCardContent(card) }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth().height(108.dp),
            shape = IonaCardShape,
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(2.dp)
        ) { DashboardCardContent(card) }
    }
}

@Composable
private fun DashboardCardContent(card: DashboardCard) {
    Row(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(LaranjaIona.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(card.icone, card.titulo, tint = LaranjaIona, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(card.titulo, style = MaterialTheme.typography.bodySmall, color = MarromSuave.copy(alpha = 0.7f))
            Text(card.valor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MarromSuave)
        }
    }
}
