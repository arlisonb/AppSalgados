package com.ionasalgados.app.presentation.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LaranjaIona = Color(0xFFE65100)
val LaranjaClaro = Color(0xFFFF9800)
val CremeQuente = Color(0xFFFFF8F0)
val MarromSuave = Color(0xFF5D4037)
val VerdeSucesso = Color(0xFF4CAF50)
val VermelhoErro = Color(0xFFF44336)
val AzulInfo = Color(0xFF2196F3)
val CinzaFundo = Color(0xFFF5F5F5)
val CinzaTexto = Color(0xFF424242)

private val LightColorScheme = lightColorScheme(
    primary = LaranjaIona,
    onPrimary = Color.White,
    primaryContainer = LaranjaClaro,
    secondary = VerdeSucesso,
    onSecondary = Color.White,
    background = CremeQuente,
    onBackground = CinzaTexto,
    surface = Color.White,
    onSurface = CinzaTexto,
    error = VermelhoErro,
    onError = Color.White
)

val Typography = Typography(
    displayLarge = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
    displaySmall = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 18.sp),
    bodyMedium = TextStyle(fontSize = 16.sp),
    bodySmall = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun IonaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}

val MenuButtonHeight = 72.dp
val CardPadding = 16.dp
val BigIconSize = 48.dp
