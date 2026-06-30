package com.ionasalgados.app.presentation.navigation

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ionasalgados.app.presentation.screens.dashboard.DashboardScreen
import com.ionasalgados.app.presentation.screens.home.HomeScreen
import com.ionasalgados.app.presentation.screens.pedidos.PedidosScreen
import com.ionasalgados.app.presentation.screens.pedidos.PedidoDetalheScreen
import com.ionasalgados.app.presentation.screens.cardapio.CardapioScreen
import com.ionasalgados.app.presentation.screens.clientes.ClientesScreen
import com.ionasalgados.app.presentation.screens.whatsapp.WhatsAppScreen
import com.ionasalgados.app.presentation.screens.configuracoes.ConfiguracoesScreen
import com.ionasalgados.app.presentation.viewmodel.MainViewModel

object Routes {
    const val HOME = "home"
    const val DASHBOARD = "dashboard"
    const val PEDIDOS = "pedidos"
    const val PEDIDO_DETALHE = "pedido/{id}"
    const val CARDAPIO = "cardapio"
    const val CLIENTES = "clientes"
    const val WHATSAPP = "whatsapp"
    const val CONFIGURACOES = "configuracoes"
}

@Composable
fun IonaNavHost(initialPedidoId: Long? = null) {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = hiltViewModel()
    val connectionStatus by mainViewModel.connectionStatus.collectAsState()
    val whatsappStatus by mainViewModel.whatsappStatus.collectAsState()
    val printerConnected by mainViewModel.printerConnected.collectAsState()
    val discovering by mainViewModel.discovering.collectAsState()
    val notification by mainViewModel.notification.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(initialPedidoId) {
        initialPedidoId?.let { id -> navController.navigate("pedido/$id") }
    }

    LaunchedEffect(notification) {
        notification?.let {
            snackbarHostState.showSnackbar(it)
            mainViewModel.clearNotification()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { _ ->
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            LaunchedEffect(Unit) { mainViewModel.refreshPrinterStatus() }
            HomeScreen(
                connectionStatus = connectionStatus,
                whatsappStatus = whatsappStatus,
                printerConnected = printerConnected,
                discovering = discovering,
                onNavigate = { route -> navController.navigate(route) },
                onRefresh = { mainViewModel.refreshData() }
            )
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PEDIDOS) {
            PedidosScreen(
                onBack = { navController.popBackStack() },
                onPedidoClick = { id -> navController.navigate("pedido/$id") }
            )
        }
        composable(Routes.PEDIDO_DETALHE) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")?.toLongOrNull() ?: 0L
            PedidoDetalheScreen(
                pedidoId = id,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.CARDAPIO) {
            CardapioScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CLIENTES) {
            ClientesScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.WHATSAPP) {
            WhatsAppScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CONFIGURACOES) {
            ConfiguracoesScreen(onBack = { navController.popBackStack() })
        }
    }
    }
}
