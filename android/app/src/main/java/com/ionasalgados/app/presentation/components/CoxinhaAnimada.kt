package com.ionasalgados.app.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun CoxinhaAnimada(modifier: Modifier = Modifier, size: Dp = 140.dp) {
    val transition = rememberInfiniteTransition(label = "coxinha")
    val bounce by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bounce"
    )
    val wiggle by transition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(480), RepeatMode.Reverse),
        label = "wiggle"
    )
    val steam by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Restart),
        label = "steam"
    )

    Canvas(
        modifier = modifier
            .size(size)
            .offset(y = (-14 * bounce).dp)
            .graphicsLayer {
                rotationZ = wiggle
                scaleX = 1f + 0.06f * bounce
                scaleY = 1f + 0.06f * bounce
            }
    ) {
        val w = this.size.width
        val h = this.size.height

        val body = Path().apply {
            moveTo(w * 0.5f, h * 0.04f)
            cubicTo(w * 0.92f, h * 0.32f, w * 0.88f, h * 0.94f, w * 0.5f, h * 0.96f)
            cubicTo(w * 0.12f, h * 0.94f, w * 0.08f, h * 0.32f, w * 0.5f, h * 0.04f)
            close()
        }
        drawPath(body, color = Color(0xFFE8A317))
        drawPath(body, color = Color(0xFFB8730A), style = Stroke(width = 4f))

        drawOval(
            color = Color(0xFFF5DEB3),
            topLeft = Offset(w * 0.28f, h * 0.7f),
            size = Size(w * 0.44f, h * 0.16f)
        )

        drawOval(
            color = Color(0xFF8B6914).copy(alpha = 0.35f),
            topLeft = Offset(w * 0.38f, h * 0.45f),
            size = Size(w * 0.12f, h * 0.08f)
        )
        drawOval(
            color = Color(0xFF8B6914).copy(alpha = 0.25f),
            topLeft = Offset(w * 0.52f, h * 0.55f),
            size = Size(w * 0.1f, h * 0.07f)
        )

        val steamAlpha = 0.4f * (1f - steam)
        drawLine(Color.White.copy(alpha = steamAlpha), Offset(w * 0.42f, h * 0.08f), Offset(w * 0.38f, h * -0.02f), strokeWidth = 3f)
        drawLine(Color.White.copy(alpha = steamAlpha * 0.8f), Offset(w * 0.5f, h * 0.05f), Offset(w * 0.5f, h * -0.05f), strokeWidth = 3f)
        drawLine(Color.White.copy(alpha = steamAlpha * 0.6f), Offset(w * 0.58f, h * 0.08f), Offset(w * 0.62f, h * -0.01f), strokeWidth = 3f)
    }
}
