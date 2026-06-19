package com.example.runh10.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

val ZoneColors = listOf(
    Color(0xFF9AA0A6), // Z1 gray
    Color(0xFF4A9EFF), // Z2 blue
    Color(0xFF34C759), // Z3 green
    Color(0xFFFF9F0A), // Z4 orange
    Color(0xFFFF3B30), // Z5 red
)

fun zoneColor(zone: Int?): Color = zone?.let { ZoneColors[(it - 1).coerceIn(0, 4)] } ?: Color(0xFF555555)

@Composable
fun ZoneRing(
    zone: Int?,
    sweepFraction: Float,
    dim: Boolean,
    modifier: Modifier = Modifier,
    ambient: Boolean = false,
) {
    val color = if (dim) Color(0xFF262626) else zoneColor(zone)
    val pathEffect = if (dim) PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f) else null

    Canvas(modifier.fillMaxSize()) {
        val strokeWidth = 14f
        val inset = strokeWidth / 2f + 2f
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val topLeft = Offset(inset, inset)

        // Ambient: draw only a thin, solid, dim outline — no colored fill, no
        // dashes or alpha (low-bit ambient panels have a tiny color depth).
        if (ambient) {
            drawArc(
                color = Color(0xFF3A3A3A),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = 3f),
            )
            return@Canvas
        }

        // Track ring
        drawArc(
            color = Color(0xFF1E1E1E),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth),
        )

        // Zone arc — dim shows dotted track color, lit shows zone color
        if (dim) {
            drawArc(
                color = Color(0xFF444444).copy(alpha = 0.5f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, pathEffect = pathEffect),
            )
        } else {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * sweepFraction.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
    }
}
