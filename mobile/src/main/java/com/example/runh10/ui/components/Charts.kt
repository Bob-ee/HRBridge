package com.example.runh10.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.runh10.ui.theme.Heat
import kotlin.math.min

/**
 * The HEAT route: GPS polyline stroked with the cool→hot effort gradient over a
 * faint grid on dark — exactly the mocks, no map SDK. [points] = [[lat,lon],...].
 */
@Composable
fun RouteHeatmap(
    points: List<List<Double>>,
    modifier: Modifier = Modifier,
    strokeWidthDp: Float = 4.5f,
    showGrid: Boolean = true,
    showEndpoints: Boolean = true,
    inset: Float = 0.12f,
) {
    Canvas(modifier.fillMaxSize()) {
        if (showGrid) drawGrid()
        if (points.size < 2) return@Canvas

        val lats = points.map { it[0] }
        val lons = points.map { it[1] }
        val latMin = lats.min(); val latMax = lats.max()
        val lonMin = lons.min(); val lonMax = lons.max()
        val latSpan = (latMax - latMin).coerceAtLeast(1e-6)
        val lonSpan = (lonMax - lonMin).coerceAtLeast(1e-6)

        val availW = size.width * (1f - inset * 2f)
        val availH = size.height * (1f - inset * 2f)
        val scale = min(availW / lonSpan.toFloat(), availH / latSpan.toFloat())
        val ox = (size.width - lonSpan.toFloat() * scale) / 2f
        val oy = (size.height - latSpan.toFloat() * scale) / 2f
        fun place(p: List<Double>) = Offset(
            x = ox + ((p[1] - lonMin).toFloat() * scale),
            y = oy + ((latMax - p[0]).toFloat() * scale),
        )

        val path = Path()
        points.forEachIndexed { i, p ->
            val o = place(p)
            if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
        }
        drawPath(
            path,
            brush = Brush.linearGradient(Heat.heatGradientColors),
            style = Stroke(width = strokeWidthDp.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        if (showEndpoints) {
            drawCircle(Color.White, radius = 5.dp.toPx() * 0.9f, center = place(points.first()))
            drawCircle(Heat.danger, radius = 4.dp.toPx(), center = place(points.last()))
            drawCircle(Color.White, radius = 4.dp.toPx(), center = place(points.last()), style = Stroke(1.5.dp.toPx()))
        }
    }
}

private fun DrawScope.drawGrid(cell: Float = 34f) {
    val c = Color.White.copy(alpha = 0.04f)
    val cellPx = cell.dp.toPx() * 0.6f
    var x = 0f
    while (x < size.width) {
        drawLine(c, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += cellPx
    }
    var y = 0f
    while (y < size.height) {
        drawLine(c, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += cellPx
    }
}

/** HR-over-time area chart: orange stroke + gradient fill fading to transparent. */
@Composable
fun HrCurveChart(
    series: List<List<Double>>, // [[offsetSec,bpm],...]
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        if (series.size < 2) return@Canvas
        val xs = series.map { it[0] }
        val ys = series.map { it[1] }
        val xMin = xs.min(); val xMax = xs.max()
        val yMin = (ys.min() - 6).coerceAtLeast(30.0)
        val yMax = ys.max() + 6
        val xSpan = (xMax - xMin).coerceAtLeast(1.0)
        val ySpan = (yMax - yMin).coerceAtLeast(1.0)
        fun place(p: List<Double>) = Offset(
            x = ((p[0] - xMin) / xSpan * size.width).toFloat(),
            y = ((yMax - p[1]) / ySpan * size.height).toFloat(),
        )
        val line = Path()
        series.forEachIndexed { i, p ->
            val o = place(p)
            if (i == 0) line.moveTo(o.x, o.y) else line.lineTo(o.x, o.y)
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            fill,
            brush = Brush.verticalGradient(
                listOf(Heat.brandOrange.copy(alpha = 0.4f), Heat.brandOrange.copy(alpha = 0f)),
            ),
        )
        drawPath(line, Heat.brandOrange, style = Stroke(width = 2.4.dp.toPx(), join = StrokeJoin.Round))
    }
}

/** Time-in-zones donut with the dominant zone's label rendered by the caller in the middle. */
@Composable
fun ZoneDonut(
    zoneMillis: List<Long>,
    modifier: Modifier = Modifier,
    thicknessFraction: Float = 0.28f,
) {
    Canvas(modifier) {
        val total = zoneMillis.sum().coerceAtLeast(1)
        var start = -90f
        val stroke = size.minDimension * thicknessFraction / 2f
        val insetPx = stroke / 2f
        val arcSize = Size(size.width - insetPx * 2, size.height - insetPx * 2)
        val tl = Offset(insetPx, insetPx)
        zoneMillis.forEachIndexed { i, ms ->
            val sweep = ms.toFloat() / total * 360f
            if (sweep > 0.5f) {
                drawArc(
                    color = Heat.zones[i],
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = tl,
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
            }
            start += sweep
        }
    }
}

/** Stacked five-segment zone-distribution bar (This Week card). */
@Composable
fun ZoneBar(zoneMillis: List<Long>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val total = zoneMillis.sum().toFloat()
        if (total <= 0) {
            drawRoundRect(Heat.surfaceDeep, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
            return@Canvas
        }
        val gap = 3.dp.toPx()
        var x = 0f
        zoneMillis.forEachIndexed { i, ms ->
            val w = (ms / total) * (size.width - gap * 4)
            if (w > 0f) {
                drawRect(Heat.zones[i], topLeft = Offset(x, 0f), size = Size(w, size.height))
                x += w + gap
            }
        }
    }
}

/** Horizontal intensity bar for split rows (amber→red fill on a deep track). */
@Composable
fun SplitBar(fraction: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
        drawRoundRect(Heat.surfaceDeep, cornerRadius = r)
        val w = size.width * fraction.coerceIn(0.05f, 1f)
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(Color(0xFFF59E0B), Color(0xFFEF4444)), endX = w),
            size = Size(w, size.height),
            cornerRadius = r,
        )
    }
}

/**
 * Phone live zone ring (mock: 212dp): conic zone base @32% opacity, active zone
 * segment at full color, glowing marker at the live HR position within its band.
 */
@Composable
fun PhoneZoneRing(
    zone: Int?,
    bpm: Int?,
    zoneEdges: List<Int>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.155f
        val insetPx = stroke / 2f
        val arcSize = Size(size.width - insetPx * 2, size.height - insetPx * 2)
        val tl = Offset(insetPx, insetPx)
        for (i in 0 until 5) {
            drawArc(
                color = Heat.zones[i].copy(alpha = 0.32f),
                startAngle = -90f + i * 72f,
                sweepAngle = 72f,
                useCenter = false,
                topLeft = tl,
                size = arcSize,
                style = Stroke(width = stroke),
            )
        }
        if (zone == null) return@Canvas
        val zi = (zone - 1).coerceIn(0, 4)
        drawArc(
            color = Heat.zones[zi],
            startAngle = -90f + zi * 72f,
            sweepAngle = 72f,
            useCenter = false,
            topLeft = tl,
            size = arcSize,
            style = Stroke(width = stroke),
        )
        if (bpm != null && zoneEdges.size == 6) {
            val lo = zoneEdges[zi]; val hi = zoneEdges[zi + 1]
            val frac = if (hi > lo) ((bpm - lo).toFloat() / (hi - lo)).coerceIn(0f, 1f) else 0.5f
            val rad = Math.toRadians(-90.0 + (zi + frac) * 72.0)
            val r = arcSize.width / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            val p = Offset(c.x + (r * kotlin.math.cos(rad)).toFloat(), c.y + (r * kotlin.math.sin(rad)).toFloat())
            drawCircle(Heat.zones[zi].copy(alpha = 0.35f), radius = 10.dp.toPx() * 0.9f, center = p)
            drawCircle(Color(0xFF0A0C0F), radius = 6.5.dp.toPx(), center = p)
            drawCircle(Heat.zones[zi], radius = 5.dp.toPx(), center = p)
        }
    }
}

/** Little live RR/HR trace for the sensor card (flatline + beats). */
@Composable
fun EkgTrace(modifier: Modifier = Modifier, color: Color = Heat.brandRed) {
    Canvas(modifier) {
        val h = size.height; val w = size.width
        val mid = h / 2f
        val p = Path().apply {
            moveTo(0f, mid)
            var x = 0f
            val beat = w / 3.2f
            while (x < w) {
                lineTo(x + beat * 0.4f, mid)
                lineTo(x + beat * 0.48f, mid - h * 0.32f)
                lineTo(x + beat * 0.56f, mid + h * 0.36f)
                lineTo(x + beat * 0.64f, mid)
                x += beat
            }
            lineTo(w, mid)
        }
        drawPath(p, color.copy(alpha = 0.85f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
