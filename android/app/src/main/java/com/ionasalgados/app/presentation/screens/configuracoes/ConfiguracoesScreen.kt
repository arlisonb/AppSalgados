package com.ionasalgados.app.presentation.screens.configuracoes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ionasalgados.app.presentation.components.*
import com.ionasalgados.app.presentation.viewmodel.ConfiguracoesViewModel

@Composable
fun ConfiguracoesScreen(
    onBack: () -> Unit,
    viewModel: ConfiguracoesViewModel = hiltViewModel()
) {
    val config by viewModel.config.collectAsState()
    val printerMacSaved by viewModel.printerMac.collectAsState()
    val message by viewModel.message.collectAsState()

    var nomeEmpresa by remember(config) { mutableStateOf(config["nome_empresa"] ?: "Iona Salgados") }
    var telefone by remember { mutableStateOf("") }
    var endereco by remember(config) { mutableStateOf(config["endereco"] ?: "") }
    var pix by remember(config) { mutableStateOf(config["pix"] ?: "") }
    var impressoraMac by remember(printerMacSaved) { mutableStateOf(printerMacSaved) }

    LaunchedEffect(Unit) { viewModel.load() }

    IonaScaffold(
        title = "Configurações",
        subtitle = "Empresa e sistema",
        onBack = onBack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            if (message.isNotEmpty()) {
                IonaMessageBanner(message)
            }

            IonaSectionTitle("Empresa")
            OutlinedTextField(nomeEmpresa, { nomeEmpresa = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            OutlinedTextField(telefone, { telefone = it }, label = { Text("Telefone") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            OutlinedTextField(endereco, { endereco = it }, label = { Text("Endereço") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            OutlinedTextField(pix, { pix = it }, label = { Text("Chave PIX") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))

            IonaSectionTitle("Impressora Bluetooth", modifier = Modifier.padding(top = 8.dp))
            OutlinedTextField(impressoraMac, { impressoraMac = it }, label = { Text("Endereço MAC") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            OutlinedButton(
                onClick = { viewModel.conectarImpressora(impressoraMac) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Conectar impressora") }

            Spacer(modifier = Modifier.height(8.dp))
            IonaPrimaryButton(
                text = "Salvar configurações",
                onClick = {
                    viewModel.saveConfig(mapOf(
                        "nome_empresa" to nomeEmpresa,
                        "telefone" to telefone,
                        "endereco" to endereco,
                        "pix" to pix
                    ))
                }
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
