package com.ionasalgados.app.presentation.screens.relatorios

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ionasalgados.app.presentation.theme.LaranjaIona
import com.ionasalgados.app.presentation.viewmodel.RelatoriosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelatoriosScreen(
    onBack: () -> Unit,
    viewModel: RelatoriosViewModel = hiltViewModel()
) {
    val relatorio by viewModel.relatorio.collectAsState()
    val dashboard by viewModel.dashboard.collectAsState()
    var periodo by remember { mutableStateOf("diario") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Relatórios", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LaranjaIona, titleContentColor = Color.White)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("diario" to "Hoje", "semanal" to "Semana", "mensal" to "Mês", "anual" to "Ano").forEach { (p, label) ->
                        FilterChip(
                            selected = periodo == p,
                            onClick = { periodo = p; viewModel.loadRelatorio(p) },
                            label = { Text(label) }
                        )
                    }
                }
            }
            item {
                relatorio?.let { r ->
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Faturamento: R$ %.2f".format(r.faturamento), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Lucro: R$ %.2f".format(r.lucro))
                            Text("Pedidos: ${r.qtd_pedidos}")
                            Text("Cancelados: ${r.pedidos_cancelados}")
                            Text("Ticket médio: R$ %.2f".format(r.ticket_medio))
                            Text("Despesas: R$ %.2f".format(r.despesas))
                        }
                    }
                } ?: Text("Carregando relatório...", style = MaterialTheme.typography.bodyLarge)
            }
            item {
                Text("Resumo do dia", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Pedidos: ${dashboard.pedidosHoje} | Vendido: R$ %.2f".format(dashboard.valorHoje))
                Text("Pendentes: ${dashboard.pendentes} | Finalizados: ${dashboard.finalizados}")
            }
        }
    }
}
