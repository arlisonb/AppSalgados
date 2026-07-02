package com.ionasalgados.app.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.ionasalgados.app.R

@Composable
fun CoxinhaAnimada(modifier: Modifier = Modifier, size: Dp = 140.dp) {
    val transition = rememberInfiniteTransition(label = "coxinha")
    val bounce by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1400
                0f at 0 using FastOutSlowInEasing
                1f at 380 using FastOutSlowInEasing
                0.2f at 520 using FastOutSlowInEasing
                0f at 1400
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "bounce"
    )
    val tilt by transition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(720, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "tilt"
    )
    val glow by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    val fundo = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .size(size)
            .background(fundo),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = "Coxinha",
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(fundo, BlendMode.Multiply),
            modifier = Modifier
                .fillMaxSize(0.92f)
                .offset(y = (-20 * bounce).dp)
                .graphicsLayer {
                    rotationZ = tilt
                    val squash = 1f - 0.08f * bounce
                    scaleX = glow * (0.96f + 0.04f * bounce)
                    scaleY = glow * (1.04f - 0.12f * bounce + squash * 0.08f)
                }
        )
    }
}
