package com.example.runh10.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.example.runh10.presentation.theme.Heat
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

fun zoneColor(zone: Int?): Color =
    zone?.let { Heat.zones[(it - 1).coerceIn(0, 4)] } ?: Heat.textFaint

fun zoneName(zone: Int?): String =
    zone?.let { Heat.zoneNames[(it - 1).coerceIn(0, 4)] } ?: "—"

/**
 * HEAT bezel zone ring: five 72° segments hugging the screen edge at 30% opacity,
 * the active zone's segment at full color, and a glowing marker dot at the live
 * HR's position within its zone band ([zoneEdges] = ZoneCalculator.edges, 6 bpm values).
 */
@Composable
fun BezelZoneRing(
    zone: Int?,
    bpm: Int?,
    zoneEdges: List<Int>,
    modifier: Modifier = Modifier,
    dim: Boolean = false,
) {
    Canvas(modifier.fillMaxSize()) {
        val stroke = 13.dp.toPx()
        val inset = stroke / 2f + 1.dp.toPx()
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val topLeft = Offset(inset, inset)

        // Base: all five segments, translucent.
        for (i in 0 until 5) {
            drawArc(
                color = Heat.zones[i].copy(alpha = if (dim) 0.12f else 0.30f),
                startAngle = -90f + i * 72f,
                sweepAngle = 72f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
        }
        if (dim || zone == null) return@Canvas

        // Active zone segment at full color.
        val zi = (zone - 1).coerceIn(0, 4)
        drawArc(
            color = Heat.zones[zi],
            startAngle = -90f + zi * 72f,
            sweepAngle = 72f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke),
        )

        // Marker dot at the HR's position within its zone band.
        if (bpm != null && zoneEdges.size == 6) {
            val lo = zoneEdges[zi]
            val hi = zoneEdges[zi + 1]
            val frac = if (hi > lo) ((bpm - lo).toFloat() / (hi - lo)).coerceIn(0f, 1f) else 0.5f
            val angleDeg = -90.0 + (zi + frac) * 72.0
            val rad = angleDeg * PI / 180.0
            val r = arcSize.width / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            val p = Offset(c.x + (r * cos(rad)).toFloat(), c.y + (r * sin(rad)).toFloat())
            val dotR = 4.5.dp.toPx() // 9dp marker dot
            drawCircle(Heat.zones[zi].copy(alpha = 0.35f), radius = dotR * 1.9f, center = p)
            drawCircle(Heat.bg, radius = dotR * 1.25f, center = p)
            drawCircle(Heat.zones[zi], radius = dotR, center = p)
        }
    }
}

/** "ZONE 4 · THRESHOLD"-style translucent chip in the zone color. */
@Composable
fun ZoneChip(zone: Int?, modifier: Modifier = Modifier) {
    val color = zoneColor(zone)
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(
            text = if (zone != null) "ZONE $zone · ${zoneName(zone).uppercase()}" else "NO ZONE",
            fontFamily = Heat.sairaCondensed,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            color = color,
        )
    }
}

/** Generic translucent chip (e.g. "TEMPO · DONE", "8s faster than avg"). */
@Composable
fun HeatChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            .padding(horizontal = 11.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontFamily = Heat.sairaCondensed,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.1.sp,
            color = color,
        )
    }
}

/** Vertical-pager position dots per the mocks. */
@Composable
fun PageDots(count: Int, active: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(7.dp)
                    .background(
                        if (i == active) Heat.brandOrange else Heat.borderStrong,
                        CircleShape,
                    ),
            )
        }
    }
}

/** Time-of-day, pinned to the top of run-data screens. */
@Composable
fun PinnedClock(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(clockText()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = clockText()
            // Re-check on the next minute boundary.
            delay(60_000L - (System.currentTimeMillis() % 60_000L) + 50L)
        }
    }
    Text(
        text = now,
        modifier = modifier,
        fontFamily = Heat.sairaCondensed,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        letterSpacing = 1.2.sp,
        color = Heat.textClock,
    )
}

private fun clockText(): String {
    val c = Calendar.getInstance()
    val h = c.get(Calendar.HOUR).let { if (it == 0) 12 else it }
    return "%d:%02d".format(h, c.get(Calendar.MINUTE))
}

/**
 * The HR Bridge logo mark: a pulse waveform (the design's HR glyph — deliberately
 * not a bridge). Draws the 24×24 glyph path scaled into [size].
 */
@Composable
fun PulseLogo(size: Dp, color: Color = Heat.brandRed, strokeWidth: Dp = 2.4.dp) {
    Canvas(Modifier.size(size)) {
        drawPulsePath(this, color, strokeWidth.toPx())
    }
}

/** Shared pulse-path renderer: M2 12 h4 l2.5 7 4 -15 3 11 1.5 -4 2 1 h4 (24×24 box). */
fun drawPulsePath(scope: DrawScope, color: Color, strokeWidthPx: Float) = with(scope) {
    val s = size.minDimension / 24f
    val p = Path().apply {
        moveTo(2f * s, 12f * s)
        lineTo(6f * s, 12f * s)
        lineTo(8.5f * s, 19f * s)
        lineTo(12.5f * s, 4f * s)
        lineTo(15.5f * s, 15f * s)
        lineTo(17f * s, 11f * s)
        lineTo(19f * s, 12f * s)
        lineTo(23f * s, 12f * s)
    }
    drawPath(
        p,
        color = color,
        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}
