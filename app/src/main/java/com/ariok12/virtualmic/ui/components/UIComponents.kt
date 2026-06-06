package com.ariok12.virtualmic.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow

@Composable
fun VuMeter(amplitudeFlow: StateFlow<Float>, modifier: Modifier = Modifier) {
    val amplitude by amplitudeFlow.collectAsState()
    val animatedAmplitude by animateFloatAsState(
        targetValue = amplitude,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "amplitude"
    )

    val barColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.fillMaxWidth().height(64.dp)) {
        val barWidth = 8.dp.toPx()
        val spacing = 4.dp.toPx()
        val totalBars = (size.width / (barWidth + spacing)).toInt()
        val centerY = size.height / 2

        for (i in 0 until totalBars) {
            // A simple pseudo-random looking pattern multiplied by the animated amplitude
            val normalizedI = (i.toFloat() / totalBars) * Math.PI.toFloat() * 2
            val barHeightScale = (Math.sin(normalizedI.toDouble()).toFloat() * 0.5f + 0.5f) * animatedAmplitude
            val actualHeight = (size.height * barHeightScale).coerceAtLeast(4.dp.toPx()) // min height

            drawLine(
                color = barColor,
                start = Offset(x = i * (barWidth + spacing), y = centerY - actualHeight / 2),
                end = Offset(x = i * (barWidth + spacing), y = centerY + actualHeight / 2),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun ConnectionStatusIndicator(isStreaming: Boolean, ipAddress: String, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isStreaming) 1200 else 800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val statusColor = if (isStreaming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val statusText = if (isStreaming) "Connected to $ipAddress" else "Disconnected"

    Row(
        modifier = modifier.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(12.dp),
            shape = CircleShape,
            color = statusColor.copy(alpha = alpha)
        ) {}
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = statusColor
        )
    }
}
