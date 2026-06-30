package com.ionasalgados.app.presentation.screens.producao

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ionasalgados.app.presentation.theme.LaranjaIona
import com.ionasalgados.app.presentation.viewmodel.ProducaoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProducaoScreen(
    onBack: () -> Unit,
    viewModel: ProducaoViewModel = hiltViewModel()
) {
    val producao by viewModel.producao.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Produção", fontWeight = FontWeight.Bold) },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Total por Produto", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            val porProduto = producao?.porProduto.orEmpty()
            if (porProduto.isEmpty()) {
                item {
                    Text("Nenhum item em produção hoje", color = Color.Gray)
                }
            } else {
                items(porProduto, key = { it.nomeProduto ?: it.hashCode().toString() }) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(item.nomeProduto ?: "Produto", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "${item.quantidadeTotal ?: 0} un",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = LaranjaIona
                            )
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Pedidos do Dia", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            val porCliente = producao?.porCliente.orEmpty()
            if (porCliente.isEmpty()) {
                item { Text("Nenhum pedido ativo", color = Color.Gray) }
            } else {
                items(porCliente, key = { "${it.numero}-${it.clienteNome}" }) { pedido ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "#${pedido.numero ?: 0} - ${pedido.clienteNome ?: "Cliente"}",
                                fontWeight = FontWeight.Bold
                            )
                            Text("Status: ${pedido.status ?: "-"}", color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}
