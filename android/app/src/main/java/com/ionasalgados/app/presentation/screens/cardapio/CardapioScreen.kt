package com.ionasalgados.app.presentation.screens.cardapio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ionasalgados.app.domain.model.Categoria
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
    var showAddDialog by remember { mutableStateOf(false) }
    var produtoSelecionado by remember { mutableStateOf<Produto?>(null) }
    var produtoEditando by remember { mutableStateOf<Produto?>(null) }
    var produtoExcluindo by remember { mutableStateOf<Produto?>(null) }

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
                onClick = { showAddDialog = true },
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
                        ProdutoCard(
                            produto = produto,
                            onClick = { produtoSelecionado = produto }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (showAddDialog) {
        ProdutoFormDialog(
            titulo = "Novo Produto",
            categorias = categorias,
            onDismiss = { showAddDialog = false },
            onConfirm = { nome, catNome, precoUn, precoCento ->
                viewModel.adicionarProduto(nome, catNome, precoUn, precoCento) { ok ->
                    if (ok) showAddDialog = false
                }
            }
        )
    }

    produtoEditando?.let { produto ->
        ProdutoFormDialog(
            titulo = "Editar Produto",
            categorias = categorias,
            produto = produto,
            onDismiss = { produtoEditando = null },
            onConfirm = { nome, catNome, precoUn, precoCento ->
                viewModel.editarProduto(produto.id, nome, catNome, precoUn, precoCento) { ok ->
                    if (ok) produtoEditando = null
                }
            }
        )
    }

    produtoSelecionado?.let { produto ->
        AlertDialog(
            onDismissRequest = { produtoSelecionado = null },
            shape = IonaCardShape,
            title = { Text(produto.nome, fontWeight = FontWeight.Bold, color = MarromSuave) },
            text = { Text("O que deseja fazer com este item?") },
            confirmButton = {
                Button(
                    onClick = {
                        produtoSelecionado = null
                        produtoEditando = produto
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LaranjaIona)
                ) { Text("Editar") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        produtoSelecionado = null
                        produtoExcluindo = produto
                    }
                ) { Text("Excluir", color = Color(0xFFF44336)) }
            }
        )
    }

    produtoExcluindo?.let { produto ->
        AlertDialog(
            onDismissRequest = { produtoExcluindo = null },
            shape = IonaCardShape,
            title = { Text("Excluir produto?") },
            text = { Text("Remover \"${produto.nome}\" do cardápio?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.excluirProduto(produto.id)
                        produtoExcluindo = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                ) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { produtoExcluindo = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun ProdutoFormDialog(
    titulo: String,
    categorias: List<Categoria>,
    produto: Produto? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Double, Double) -> Unit
) {
    var nome by remember(produto) { mutableStateOf(produto?.nome ?: "") }
    var categoriaNome by remember(produto) {
        mutableStateOf(produto?.categoriaNome ?: categorias.firstOrNull()?.nome ?: "")
    }
    var precoUnidade by remember(produto) {
        mutableStateOf(
            produto?.let { p ->
                val un = if (p.precoUnidade > 0) p.precoUnidade
                else if (p.precoCento > 0) p.precoCento / 100 else 0.0
                if (un > 0) "%.2f".format(un).replace('.', ',') else ""
            } ?: ""
        )
    }
    var precoCento by remember(produto) {
        mutableStateOf(
            produto?.takeIf { it.precoCento > 0 }?.let { "%.2f".format(it.precoCento).replace('.', ',') } ?: ""
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = IonaCardShape,
        title = { Text(titulo, fontWeight = FontWeight.Bold, color = MarromSuave) },
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
                OutlinedTextField(
                    precoUnidade,
                    { precoUnidade = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                    label = { Text("Preço unitário (R$)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    precoCento,
                    { precoCento = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                    label = { Text("Preço cento (R$)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nome.isNotBlank() && categoriaNome.isNotBlank()) {
                        onConfirm(
                            nome,
                            categoriaNome,
                            precoUnidade.replace(',', '.').toDoubleOrNull() ?: 0.0,
                            precoCento.replace(',', '.').toDoubleOrNull() ?: 0.0
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = LaranjaIona)
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun ProdutoCard(produto: Produto, onClick: () -> Unit) {
    IonaSurfaceCard(onClick = onClick) {
        Column {
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
    }
}
