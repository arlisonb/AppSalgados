package com.ionasalgados.app.presentation.screens.whatsapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ionasalgados.app.presentation.components.*
import com.ionasalgados.app.presentation.theme.MarromSuave
import com.ionasalgados.app.presentation.viewmodel.WhatsAppViewModel
import com.ionasalgados.app.util.PhoneUtils
import kotlinx.coroutines.launch

@Composable
fun WhatsAppScreen(
    onBack: () -> Unit,
    viewModel: WhatsAppViewModel = hiltViewModel()
) {
    val pairingCode by viewModel.pairingCode.collectAsState()
    val message by viewModel.message.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var telefone by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.load() }

    IonaScaffold(
        title = "WhatsApp",
        subtitle = "Conexão do bot",
        onBack = onBack,
        headerGreen = true,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        actions = {
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(Icons.Default.Refresh, "Atualizar", tint = Color.White)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            if (message.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = IonaCardShape,
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF25D366).copy(alpha = 0.12f))
                ) {
                    Text(message, modifier = Modifier.padding(14.dp), color = Color(0xFF128C7E), fontWeight = FontWeight.Medium)
                }
            }

            if (isConnected) {
                Spacer(modifier = Modifier.height(16.dp))
                CoxinhaAnimada(size = 160.dp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Bot conectado!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF128C7E)
                )
                Text(
                    "O atendimento automático está ativo",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MarromSuave.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                IonaPrimaryButton(
                    text = "Desconectar WhatsApp",
                    onClick = { viewModel.desconectar() },
                    containerColor = Color(0xFFF44336)
                )
            } else {
                CoxinhaAnimada(size = 112.dp)
                IonaSectionTitle("Número para atendimento", modifier = Modifier.fillMaxWidth())
                Text(
                    "Só DDD e número — o 55 do Brasil é adicionado automaticamente",
                    style = MaterialTheme.typography.bodySmall,
                    color = MarromSuave.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = PhoneUtils.formatMasked(telefone),
                    onValueChange = { telefone = PhoneUtils.formatInput(it) },
                    label = { Text("DDD + número") },
                    placeholder = { Text("(34) 99966-7768") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                IonaPrimaryButton(
                    text = "Gerar código de conexão",
                    onClick = { viewModel.gerarCodigo(telefone) },
                    containerColor = Color(0xFF25D366)
                )

                pairingCode?.let { code ->
                    IonaSurfaceCard(elevation = 4.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text("Código de conexão", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MarromSuave)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                code,
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 6.sp,
                                color = Color(0xFF128C7E),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("codigo_whatsapp", code))
                                    scope.launch { snackbarHostState.showSnackbar("Código copiado!") }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Copiar código")
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "WhatsApp → Aparelhos conectados → Conectar aparelho → Digite o código",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MarromSuave.copy(alpha = 0.7f),
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
