package com.example.runh10.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.example.runh10.presentation.components.HeatChip
import com.example.runh10.presentation.theme.Heat
import com.example.runh10.shared.model.Split
import com.example.runh10.workout.UiState
import kotlin.math.roundToInt

/** Post-run summary (HEAT): faint route sketch, RUN·DONE chip, hero distance, AVG HR / HRV / KCAL. */
@Composable
fun SummaryScreen(ui: UiState, onDone: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Heat.bg)) {
        // Faint heat-gradient route behind everything.
        if (ui.routePoints.size >= 2) {
            RouteSketch(
                points = ui.routePoints,
                modifier = Modifier.fillMaxSize().padding(46.dp),
                alpha = 0.16f,
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(top = 42.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            HeatChip("RUN · DONE", Heat.brandOrange)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatMilesShort(ui.distanceMeters),
                    fontFamily = Heat.sairaCondensed,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 66.sp,
                    lineHeight = 58.sp,
                    color = Heat.text,
                )
                Text(
                    text = "MILES · ${formatElapsed(ui.elapsedSec)}",
                    fontSize = 12.sp,
                    letterSpacing = 1.7.sp,
                    color = Heat.textMuted,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    SummaryStat(weightedAvgHrValue(ui.splits)?.toString() ?: "—", "AVG HR", Heat.brandOrange)
                    SummaryStat(ui.hrvMs?.roundToInt()?.toString() ?: "—", "HRV ms", Heat.hrvPurple)
                    SummaryStat(ui.kcal?.roundToInt()?.toString() ?: "—", "KCAL", Heat.text)
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CheckIcon()
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Saved · syncs via phone",
                        fontFamily = Heat.sairaCondensed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Heat.goodGreen,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .background(Heat.surface, RoundedCornerShape(15.dp))
                        .clickable(onClick = onDone)
                        .padding(horizontal = 26.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "DONE",
                        fontFamily = Heat.sairaCondensed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 1.5.sp,
                        color = Heat.textMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontFamily = Heat.sairaCondensed,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 23.sp,
            color = color,
        )
        Text(text = label, fontSize = 9.sp, letterSpacing = 1.sp, color = Heat.textDim)
    }
}

@Composable
private fun CheckIcon() {
    Canvas(Modifier.width(14.dp).height(14.dp)) {
        val w = size.width; val h = size.height
        val p = Path().apply {
            moveTo(w * 0.12f, h * 0.55f)
            lineTo(w * 0.4f, h * 0.82f)
            lineTo(w * 0.9f, h * 0.2f)
        }
        drawPath(p, Heat.goodGreen, style = Stroke(width = w * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * Normalizes lat/lon points into the canvas and strokes them with the cool→hot
 * heat gradient (aspect preserved, north up).
 */
@Composable
fun RouteSketch(points: List<Pair<Double, Double>>, modifier: Modifier = Modifier, alpha: Float = 1f) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val lats = points.map { it.first }
        val lons = points.map { it.second }
        val latMin = lats.min(); val latMax = lats.max()
        val lonMin = lons.min(); val lonMax = lons.max()
        val latSpan = (latMax - latMin).coerceAtLeast(1e-6)
        val lonSpan = (lonMax - lonMin).coerceAtLeast(1e-6)
        val scale = minOf(size.width / lonSpan.toFloat(), size.height / latSpan.toFloat())
        val ox = (size.width - lonSpan.toFloat() * scale) / 2f
        val oy = (size.height - latSpan.toFloat() * scale) / 2f
        fun toOffset(p: Pair<Double, Double>): Offset = Offset(
            x = ox + ((p.second - lonMin).toFloat() * scale),
            y = oy + ((latMax - p.first).toFloat() * scale),
        )
        val path = Path()
        points.forEachIndexed { i, p ->
            val o = toOffset(p)
            if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
        }
        drawPath(
            path,
            brush = Brush.linearGradient(Heat.zones.drop(1).map { it.copy(alpha = alpha) }),
            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** Weighted average HR across splits (weight = moving ms); null when no split has HR. */
internal fun weightedAvgHrValue(splits: List<Split>): Int? {
    val valid = splits.filter { it.avgBpm != null }
    if (valid.isEmpty()) return null
    val w = valid.sumOf { it.movingDurationMs.toDouble() }
    if (w <= 0.0) return null
    return (valid.sumOf { it.avgBpm!! * it.movingDurationMs.toDouble() } / w).toInt()
}

/** String form kept for the existing WeightedAvgHrTest. */
internal fun weightedAvgHr(splits: List<Split>): String =
    weightedAvgHrValue(splits)?.let { "$it bpm" } ?: "—"
