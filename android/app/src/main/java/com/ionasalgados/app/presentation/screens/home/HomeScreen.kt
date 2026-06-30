package com.ionasalgados.app.presentation.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ionasalgados.app.presentation.navigation.Routes
import com.ionasalgados.app.presentation.theme.CremeQuente
import com.ionasalgados.app.presentation.theme.LaranjaClaro
import com.ionasalgados.app.presentation.theme.LaranjaIona
import com.ionasalgados.app.presentation.theme.MarromSuave
import com.ionasalgados.app.presentation.viewmodel.ConnectionStatus

data class MenuItem(
    val titulo: String,
    val subtitulo: String,
    val icone: ImageVector,
    val rota: String,
    val destaque: Boolean = false
)

private val menuItems = listOf(
    MenuItem("Pedidos", "Ver e gerenciar", Icons.Default.ShoppingBag, Routes.PEDIDOS, destaque = true),
    MenuItem("Painel", "Resumo do dia", Icons.Default.Dashboard, Routes.DASHBOARD),
    MenuItem("Cardápio", "Produtos e preços", Icons.Default.Restaurant, Routes.CARDAPIO),
    MenuItem("Clientes", "Cadastro", Icons.Default.People, Routes.CLIENTES),
    MenuItem("WhatsApp", "Conexão do bot", Icons.AutoMirrored.Filled.Chat, Routes.WHATSAPP),
    MenuItem("Configurações", "Empresa e sistema", Icons.Default.Settings, Routes.CONFIGURACOES)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    connectionStatus: ConnectionStatus,
    whatsappStatus: String = "",
    printerConnected: Boolean = false,
    discovering: Boolean = false,
    onNavigate: (String) -> Unit,
    onRefresh: () -> Unit = {}
) {
    Scaffold(
        containerColor = CremeQuente,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Iona Salgados",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        StatusChips(
                            discovering = discovering,
                            whatsappStatus = whatsappStatus,
                            printerConnected = printerConnected
                        )
                    }
                },
                actions = {
                    ConnectionBadge(connectionStatus)
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, "Atualizar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                modifier = Modifier.background(
                    Brush.verticalGradient(
                        colors = listOf(LaranjaIona, LaranjaClaro)
                    )
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "O que deseja fazer?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MarromSuave
            )
            Text(
                "Tudo para o dia a dia da sua lanchonete 🥟",
                style = MaterialTheme.typography.bodyMedium,
                color = MarromSuave.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(menuItems) { item ->
                    MenuCard(item = item, onClick = { onNavigate(item.rota) })
                }
            }
        }
    }
}

@Composable
private fun StatusChips(
    discovering: Boolean,
    whatsappStatus: String,
    printerConnected: Boolean
) {
    val waOk = whatsappStatus in listOf("conectado", "isLogged", "inChat")
    val statusText = when {
        discovering -> "Conectando ao servidor..."
        whatsappStatus.isNotEmpty() && waOk -> "WhatsApp conectado"
        whatsappStatus.isNotEmpty() -> "WhatsApp: toque no menu verde"
        else -> null
    }

    Column(modifier = Modifier.padding(top = 2.dp)) {
        statusText?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
        if (printerConnected) {
            Text(
                "Impressora conectada",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
private fun MenuCard(item: MenuItem, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val iconBg = if (item.destaque) LaranjaIona.copy(alpha = 0.15f) else LaranjaClaro.copy(alpha = 0.12f)
    val iconTint = if (item.destaque) LaranjaIona else LaranjaIona.copy(alpha = 0.85f)

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(if (item.destaque) 148.dp else 132.dp),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (item.destaque) 6.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icone,
                    contentDescription = item.titulo,
                    modifier = Modifier.size(26.dp),
                    tint = iconTint
                )
            }
            Column {
                Text(
                    text = item.titulo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MarromSuave
                )
                Text(
                    text = item.subtitulo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MarromSuave.copy(alpha = 0.55f)
                )
            }
        }
    }
}

@Composable
private fun ConnectionBadge(status: ConnectionStatus) {
    val (cor, texto) = when (status) {
        ConnectionStatus.CONNECTED -> Color(0xFF2E7D32) to "Online"
        ConnectionStatus.DISCONNECTED -> Color(0xFFC62828) to "Offline"
        ConnectionStatus.RECONNECTING -> Color(0xFFE65100) to "..."
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.22f),
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(cor)
            )
            Text(
                texto,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
