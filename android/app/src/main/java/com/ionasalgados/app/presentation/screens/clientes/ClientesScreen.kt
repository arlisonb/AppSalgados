package com.ionasalgados.app.presentation.screens.clientes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ionasalgados.app.domain.model.Cliente
import com.ionasalgados.app.presentation.theme.LaranjaIona
import com.ionasalgados.app.presentation.viewmodel.ClientesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientesScreen(
    onBack: () -> Unit,
    viewModel: ClientesViewModel = hiltViewModel()
) {
    val clientes by viewModel.clientes.collectAsState(initial = emptyList())
    val message by viewModel.message.collectAsState()

    LaunchedEffect(message) {
        if (message.isNotBlank()) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clientes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LaranjaIona, titleContentColor = Color.White)
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (message.isNotBlank()) {
                Text(
                    message,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = if (message.contains("excluído", ignoreCase = true)) Color(0xFF4CAF50) else Color(0xFFF44336),
                    fontWeight = FontWeight.Medium
                )
            }
            if (clientes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Nenhum cliente cadastrado")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(clientes, key = { it.id }) { cliente ->
                        ClienteCard(
                            cliente = cliente,
                            onDelete = { viewModel.excluirCliente(cliente.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClienteCard(cliente: Cliente, onDelete: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }
    val podeExcluir = cliente.pedidosAtivos == 0

    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(cliente.nome, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(cliente.telefone, style = MaterialTheme.typography.bodyLarge)
                cliente.endereco?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.Gray) }
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("${cliente.qtdPedidos} pedidos", style = MaterialTheme.typography.bodySmall)
                    Text("R$ %.2f".format(cliente.valorTotal), style = MaterialTheme.typography.bodySmall, color = LaranjaIona)
                }
            }
            IconButton(
                onClick = { showConfirm = true },
                enabled = podeExcluir
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = if (podeExcluir) "Excluir" else "Possui pedidos em andamento",
                    tint = if (podeExcluir) Color(0xFFF44336) else Color.Gray
                )
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Excluir cliente?") },
            text = {
                Text(
                    if (cliente.qtdPedidos > 0) {
                        "Remover \"${cliente.nome}\" da lista? O histórico de ${cliente.qtdPedidos} pedido(s) será mantido."
                    } else {
                        "Remover \"${cliente.nome}\" da lista?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}
