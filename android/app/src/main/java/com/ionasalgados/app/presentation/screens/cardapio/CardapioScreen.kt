package com.ionasalgados.app.presentation.screens.cardapio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.ionasalgados.app.presentation.theme.LaranjaIona
import com.ionasalgados.app.presentation.viewmodel.CardapioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardapioScreen(
    onBack: () -> Unit,
    viewModel: CardapioViewModel = hiltViewModel()
) {
    val produtos by viewModel.produtos.collectAsState(initial = emptyList())
    val categorias by viewModel.categorias.collectAsState(initial = emptyList())
    val message by viewModel.message.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cardápio", fontWeight = FontWeight.Bold) },
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
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }, containerColor = LaranjaIona) {
                Icon(Icons.Default.Add, "Adicionar", tint = Color.White)
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (message.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = LaranjaIona.copy(alpha = 0.12f))
                ) {
                    Text(message, modifier = Modifier.padding(12.dp), color = LaranjaIona)
                }
            }
        if (produtos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text("Nenhum produto cadastrado", style = MaterialTheme.typography.titleLarge)
                    Text("Toque no + para adicionar", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(produtos, key = { it.id }) { produto ->
                    ProdutoCard(
                        produto = produto,
                        onDelete = { viewModel.excluirProduto(produto.id) }
                    )
                }
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
        title = { Text("Novo Produto", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(nome, { nome = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = categoriaNome,
                    onValueChange = { categoriaNome = it },
                    label = { Text("Categoria") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ex: Salgados, Doces...") },
                    supportingText = if (categorias.isNotEmpty()) {
                        { Text("Existentes: ${categorias.joinToString(", ") { it.nome }}") }
                    } else null
                )
                OutlinedTextField(precoUnidade, { precoUnidade = it }, label = { Text("Preço unitário (R$)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(precoCento, { precoCento = it }, label = { Text("Preço cento — 100 un (R$)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                if (nome.isNotBlank() && categoriaNome.isNotBlank()) {
                    onConfirm(nome, categoriaNome, precoUnidade.toDoubleOrNull() ?: 0.0, precoCento.toDoubleOrNull() ?: 0.0)
                }
            }) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun ProdutoCard(produto: Produto, onDelete: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(produto.nome, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(produto.categoriaNome ?: "", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    val unidade = if (produto.precoUnidade > 0) produto.precoUnidade
                    else if (produto.precoCento > 0) produto.precoCento / 100 else 0.0
                    if (unidade > 0) Text("Un: R$ %.2f  ".format(unidade))
                    if (produto.precoCento > 0) Text("Cento: R$ %.2f".format(produto.precoCento))
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
            title = { Text("Excluir produto?") },
            text = { Text("Remover \"${produto.nome}\" do cardápio?") },
            confirmButton = {
                Button(onClick = { showConfirm = false; onDelete() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}
