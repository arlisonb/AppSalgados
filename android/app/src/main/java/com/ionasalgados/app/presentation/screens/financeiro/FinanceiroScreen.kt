package com.ionasalgados.app.presentation.screens.financeiro

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
import com.ionasalgados.app.presentation.viewmodel.FinanceiroViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceiroScreen(
    onBack: () -> Unit,
    viewModel: FinanceiroViewModel = hiltViewModel()
) {
    val resumo by viewModel.resumo.collectAsState()
    val caixa by viewModel.caixa.collectAsState()
    var valorAbertura by remember { mutableStateOf("0") }
    var showFechar by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Financeiro", fontWeight = FontWeight.Bold) },
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
                resumo?.let { r ->
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Hoje: R$ %.2f".format(r.faturamentoHoje), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Mês: R$ %.2f".format(r.faturamentoMes))
                            Text("PIX: R$ %.2f | Dinheiro: R$ %.2f".format(r.pixRecebido, r.dinheiroRecebido))
                        }
                    }
                }
            }
            item {
                caixa?.let { c ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(if (c.aberto) "Caixa ABERTO" else "Caixa FECHADO", fontWeight = FontWeight.Bold, color = if (c.aberto) Color(0xFF4CAF50) else Color.Gray)
                            if (c.aberto) Text("Valor inicial: R$ %.2f".format(c.valorInicial))
                        }
                    }
                }
            }
            item {
                if (caixa?.aberto != true) {
                    OutlinedTextField(valorAbertura, { valorAbertura = it }, label = { Text("Valor inicial") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = { viewModel.abrirCaixa(valorAbertura.toDoubleOrNull() ?: 0.0) },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) { Text("Abrir Caixa") }
                } else {
                    Button(
                        onClick = { showFechar = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                    ) { Text("Fechar Caixa") }
                }
            }
        }
    }

    if (showFechar) {
        AlertDialog(
            onDismissRequest = { showFechar = false },
            title = { Text("Fechar caixa?") },
            confirmButton = {
                Button(onClick = { viewModel.fecharCaixa(""); showFechar = false }) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { showFechar = false }) { Text("Cancelar") }
            }
        )
    }
}
