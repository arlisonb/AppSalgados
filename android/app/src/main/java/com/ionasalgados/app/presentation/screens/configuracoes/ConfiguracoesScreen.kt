package com.ionasalgados.app.presentation.screens.configuracoes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ionasalgados.app.BuildConfig
import com.ionasalgados.app.presentation.theme.LaranjaIona
import com.ionasalgados.app.presentation.viewmodel.ConfiguracoesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfiguracoesScreen(
    onBack: () -> Unit,
    viewModel: ConfiguracoesViewModel = hiltViewModel()
) {
    val config by viewModel.config.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val printerMacSaved by viewModel.printerMac.collectAsState()
    val message by viewModel.message.collectAsState()
    val discovering by viewModel.discovering.collectAsState()

    var nomeEmpresa by remember(config) { mutableStateOf(config["nome_empresa"] ?: "Iona Salgados") }
    var telefone by remember(config) { mutableStateOf(config["telefone"] ?: "") }
    var endereco by remember(config) { mutableStateOf(config["endereco"] ?: "") }
    var pix by remember(config) { mutableStateOf(config["pix"] ?: "") }
    var impressoraMac by remember(printerMacSaved) { mutableStateOf(printerMacSaved) }

    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LaranjaIona, titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (message.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = LaranjaIona.copy(alpha = 0.1f))) {
                    Text(message, modifier = Modifier.padding(12.dp), color = LaranjaIona)
                }
            }

            Text("Servidor", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (BuildConfig.USE_PRODUCTION_SERVER) {
                            "Servidor de produção (internet)"
                        } else {
                            "Detectado automaticamente na rede Wi-Fi"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    if (serverUrl.isNotBlank()) {
                        Text(serverUrl, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    }
                    if (discovering) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = LaranjaIona)
                        Text(
                            if (BuildConfig.USE_PRODUCTION_SERVER) "Conectando..." else "Procurando servidor...",
                            color = Color.Gray
                        )
                    }
                }
            }
            Button(
                onClick = { viewModel.buscarServidor() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = !discovering
            ) {
                Text(
                    if (BuildConfig.USE_PRODUCTION_SERVER) "Reconectar ao servidor" else "Buscar servidor novamente"
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Empresa", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(nomeEmpresa, { nomeEmpresa = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(telefone, { telefone = it }, label = { Text("Telefone") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(endereco, { endereco = it }, label = { Text("Endereço") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(pix, { pix = it }, label = { Text("Chave PIX") }, modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(8.dp))
            Text("Impressora Bluetooth", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(impressoraMac, { impressoraMac = it }, label = { Text("Endereço MAC") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { viewModel.conectarImpressora(impressoraMac) }, modifier = Modifier.fillMaxWidth()) {
                Text("Conectar impressora")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    viewModel.saveConfig(mapOf(
                        "nome_empresa" to nomeEmpresa,
                        "telefone" to telefone,
                        "endereco" to endereco,
                        "pix" to pix
                    ))
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LaranjaIona)
            ) {
                Text("Salvar", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
