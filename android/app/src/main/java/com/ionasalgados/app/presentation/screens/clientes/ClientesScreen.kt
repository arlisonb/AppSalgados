package com.ionasalgados.app.presentation.screens.clientes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import com.ionasalgados.app.presentation.components.*
import com.ionasalgados.app.presentation.theme.LaranjaIona
import com.ionasalgados.app.presentation.theme.MarromSuave
import com.ionasalgados.app.presentation.viewmodel.ClientesViewModel

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

    IonaScaffold(
        title = "Clientes",
        subtitle = "Cadastro",
        onBack = onBack
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (message.isNotBlank()) {
                IonaMessageBanner(
                    message = message,
                    success = message.contains("excluído", ignoreCase = true),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }
            if (clientes.isEmpty()) {
                IonaEmptyState("Nenhum cliente cadastrado", "Clientes aparecem ao fazer pedidos")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                    items(clientes, key = { it.id }) { cliente ->
                        ClienteCard(cliente = cliente, onDelete = { viewModel.excluirCliente(cliente.id) })
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun ClienteCard(cliente: Cliente, onDelete: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }
    val podeExcluir = cliente.pedidosAtivos == 0

    IonaSurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(cliente.nome, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MarromSuave)
                Text(cliente.telefone, style = MaterialTheme.typography.bodyLarge, color = MarromSuave.copy(alpha = 0.8f))
                cliente.endereco?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MarromSuave.copy(alpha = 0.55f))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("${cliente.qtdPedidos} pedidos", style = MaterialTheme.typography.bodySmall, color = MarromSuave.copy(alpha = 0.6f))
                    Text("R$ %.2f".format(cliente.valorTotal), style = MaterialTheme.typography.bodySmall, color = LaranjaIona, fontWeight = FontWeight.Medium)
                }
            }
            IconButton(onClick = { showConfirm = true }, enabled = podeExcluir) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = if (podeExcluir) "Excluir" else "Possui pedidos em andamento",
                    tint = if (podeExcluir) Color(0xFFF44336) else MarromSuave.copy(alpha = 0.3f)
                )
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            shape = IonaCardShape,
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
                    onClick = { showConfirm = false; onDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                ) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancelar") } }
        )
    }
}
