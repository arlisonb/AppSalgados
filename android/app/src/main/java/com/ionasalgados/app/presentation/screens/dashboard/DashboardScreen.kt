package com.ionasalgados.app.presentation.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ionasalgados.app.domain.model.Dashboard
import com.ionasalgados.app.domain.model.Pedido
import com.ionasalgados.app.presentation.theme.LaranjaIona
import com.ionasalgados.app.presentation.viewmodel.DashboardViewModel

data class DashboardCard(
    val titulo: String,
    val valor: String,
    val icone: ImageVector,
    val cor: Color,
    val clicavel: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Painel", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadDashboard() }) {
                        Icon(Icons.Default.Refresh, "Atualizar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LaranjaIona, titleContentColor = Color.White)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (message.isNotBlank()) {
                item {
                    Text(
                        message,
                        color = if (message.contains("não conectada", ignoreCase = true) || message.contains("não encontrado", ignoreCase = true)) {
                            Color(0xFFF44336)
                        } else {
                            Color(0xFF4CAF50)
                        },
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 8.dp)
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
                item {
                    Text(
                        "Pedidos de hoje",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                if (pedidos.isEmpty()) {
                    item {
                        Text(
                            "Nenhum pedido hoje",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                } else {
                    items(pedidos, key = { it.id }) { pedido ->
                        PedidoHojeCard(
                            pedido = pedido,
                            onImprimir = { viewModel.imprimirPedido(pedido.id) }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun PedidoHojeCard(pedido: Pedido, onImprimir: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "#${pedido.numero} — ${pedido.clienteNome ?: "Cliente"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    pedido.status.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
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
                Spacer(modifier = Modifier.height(4.dp))
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
        DashboardCard("Pedidos Hoje", dashboard.pedidosHoje.toString(), Icons.Default.ShoppingCart, Color(0xFF2196F3), clicavel = true),
        DashboardCard("Faturamento Mensal", "R$ %.0f".format(dashboard.valorMes), Icons.Default.CalendarMonth, Color(0xFF607D8B)),
        DashboardCard("Faturamento Diário", "R$ %.0f".format(dashboard.valorHoje), Icons.Default.AttachMoney, Color(0xFF00897B)),
        DashboardCard("Finalizados", dashboard.finalizados.toString(), Icons.Default.CheckCircle, Color(0xFF4CAF50)),
        DashboardCard("Clientes", dashboard.clientesAtendidos.toString(), Icons.Default.People, Color(0xFF795548)),
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.height(320.dp)
    ) {
        items(cards) { card ->
            val selecionado = card.clicavel && pedidosHojeSelecionado
            val border = if (selecionado) BorderStroke(2.dp, card.cor) else null

            if (card.clicavel) {
                Card(
                    onClick = onPedidosHojeClick,
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = card.cor.copy(alpha = if (selecionado) 0.2f else 0.1f)),
                    border = border
                ) {
                    DashboardCardContent(card)
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = card.cor.copy(alpha = 0.1f))
                ) {
                    DashboardCardContent(card)
                }
            }
        }
    }
}

@Composable
private fun DashboardCardContent(card: DashboardCard) {
    Row(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(card.icone, card.titulo, tint = card.cor, modifier = Modifier.size(36.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(card.titulo, style = MaterialTheme.typography.bodySmall, color = card.cor)
            Text(card.valor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = card.cor)
        }
    }
}
