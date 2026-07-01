package com.ionasalgados.app.presentation.screens.cardapio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ionasalgados.app.domain.model.Produto
import com.ionasalgados.app.presentation.components.*
import com.ionasalgados.app.presentation.theme.LaranjaIona
import com.ionasalgados.app.presentation.theme.MarromSuave
import com.ionasalgados.app.presentation.viewmodel.CardapioViewModel

@Composable
fun CardapioScreen(
    onBack: () -> Unit,
    viewModel: CardapioViewModel = hiltViewModel()
) {
    val produtos by viewModel.produtos.collectAsState(initial = emptyList())
    val categorias by viewModel.categorias.collectAsState(initial = emptyList())
    val message by viewModel.message.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    IonaScaffold(
        title = "Cardápio",
        subtitle = "Produtos e preços",
        onBack = onBack,
        actions = {
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(Icons.Default.Refresh, "Atualizar", tint = Color.White)
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showDialog = true },
                containerColor = LaranjaIona,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, "Adicionar", tint = Color.White)
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (message.isNotEmpty()) {
                IonaMessageBanner(message, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
            if (produtos.isEmpty()) {
                IonaEmptyState("Nenhum produto cadastrado", "Toque no + para adicionar")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                    items(produtos, key = { it.id }) { produto ->
                        ProdutoCard(produto = produto, onDelete = { viewModel.excluirProduto(produto.id) })
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (showDialog) {
        AdicionarProdutoDialog(
            categorias = categorias,
            onDismiss = { showDialog = false },
            onConfirm = { nome, catNome, precoUn, precoCento ->
                viewModel.adicionarProduto(nome, catNome, precoUn, precoCento) { ok ->
                    if (ok) showDialog = false
                }
            }
        )
    }
}

@Composable
private fun AdicionarProdutoDialog(
    categorias: List<com.ionasalgados.app.domain.model.Categoria>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Double, Double) -> Unit
) {
    var nome by remember { mutableStateOf("") }
    var categoriaNome by remember { mutableStateOf(categorias.firstOrNull()?.nome ?: "") }
    var precoUnidade by remember { mutableStateOf("") }
    var precoCento by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = IonaCardShape,
        title = { Text("Novo Produto", fontWeight = FontWeight.Bold, color = MarromSuave) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(nome, { nome = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(
                    value = categoriaNome,
                    onValueChange = { categoriaNome = it },
                    label = { Text("Categoria") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    placeholder = { Text("Ex: Salgados, Doces...") }
                )
                OutlinedTextField(precoUnidade, { precoUnidade = it }, label = { Text("Preço unitário (R$)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(precoCento, { precoCento = it }, label = { Text("Preço cento (R$)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nome.isNotBlank() && categoriaNome.isNotBlank()) {
                        onConfirm(nome, categoriaNome, precoUnidade.toDoubleOrNull() ?: 0.0, precoCento.toDoubleOrNull() ?: 0.0)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = LaranjaIona)
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun ProdutoCard(produto: Produto, onDelete: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }

    IonaSurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(produto.nome, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MarromSuave)
                Text(produto.categoriaNome ?: "", style = MaterialTheme.typography.bodySmall, color = MarromSuave.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(6.dp))
                Row {
                    val unidade = if (produto.precoUnidade > 0) produto.precoUnidade
                    else if (produto.precoCento > 0) produto.precoCento / 100 else 0.0
                    if (unidade > 0) Text("Un: R$ %.2f  ".format(unidade), color = MarromSuave)
                    if (produto.precoCento > 0) Text("Cento: R$ %.2f".format(produto.precoCento), color = LaranjaIona, fontWeight = FontWeight.Medium)
                }
            }
            IconButton(onClick = { showConfirm = true }) {
                Icon(Icons.Default.Delete, "Excluir", tint = Color(0xFFF44336))
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            shape = IonaCardShape,
            title = { Text("Excluir produto?") },
            text = { Text("Remover \"${produto.nome}\" do cardápio?") },
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
