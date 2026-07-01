package com.ionasalgados.app.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ionasalgados.app.presentation.theme.CremeQuente
import com.ionasalgados.app.presentation.theme.LaranjaClaro
import com.ionasalgados.app.presentation.theme.LaranjaIona
import com.ionasalgados.app.presentation.theme.MarromSuave

val IonaCardShape = RoundedCornerShape(20.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IonaScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    headerGreen: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val gradient = if (headerGreen) {
        listOf(Color(0xFF25D366), Color(0xFF128C7E))
    } else {
        listOf(LaranjaIona, LaranjaClaro)
    }

    Scaffold(
        modifier = modifier,
        containerColor = CremeQuente,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        subtitle?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Color.White)
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                modifier = Modifier.background(Brush.verticalGradient(gradient))
            )
        },
        content = content
    )
}

@Composable
fun IonaSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MarromSuave,
        modifier = modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun IonaSurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    elevation: androidx.compose.ui.unit.Dp = 2.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = CardDefaults.cardColors(containerColor = Color.White)
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = IonaCardShape,
            colors = colors,
            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
            content = { Column(Modifier.padding(16.dp), content = content) }
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = IonaCardShape,
            colors = colors,
            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
            content = { Column(Modifier.padding(16.dp), content = content) }
        )
    }
}

@Composable
fun IonaMessageBanner(
    message: String,
    success: Boolean = true,
    modifier: Modifier = Modifier
) {
    val bg = if (success) LaranjaIona.copy(alpha = 0.12f) else Color(0xFFF44336).copy(alpha = 0.1f)
    val fg = if (success) LaranjaIona else Color(0xFFC62828)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = IonaCardShape,
        colors = CardDefaults.cardColors(containerColor = bg)
    ) {
        Text(message, modifier = Modifier.padding(14.dp), color = fg, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun IonaEmptyState(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MarromSuave)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MarromSuave.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
fun IonaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = LaranjaIona
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor)
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
