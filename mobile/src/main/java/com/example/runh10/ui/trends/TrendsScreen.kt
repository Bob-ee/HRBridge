package com.example.runh10.ui.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.runh10.data.AthleteProfile
import com.example.runh10.data.AthleteStore
import com.example.runh10.data.RunRepository
import com.example.runh10.data.RunSummaryEntity
import com.example.runh10.ui.components.Fmt
import com.example.runh10.ui.components.HeatCard
import com.example.runh10.ui.components.SectionLabel
import com.example.runh10.ui.components.StatBlock
import com.example.runh10.ui.theme.Heat
import java.util.Calendar
import kotlin.math.roundToInt

/** Trends: weekly volume bars + HRV trend (repo feature not in the mocks, HEAT-styled). */
@Composable
fun TrendsScreen(bottomPadding: PaddingValues) {
    val context = LocalContext.current
    val repo = remember { RunRepository.get(context) }
    val runs = repo.observeAll().collectAsState(initial = emptyList()).value
    val profile = remember { AthleteStore(context) }.profile.collectAsState(initial = AthleteProfile()).value
    val miles = profile.unitsMiles

    val weeks = remember(runs) { weeklyBuckets(runs, 8) }
    val hrvPoints = remember(runs) {
        runs.sortedBy { it.startMs }.mapNotNull { r -> r.hrvMs?.let { r.startMs to it } }.takeLast(20)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Heat.bg)
            .verticalScroll(rememberScrollState())
            .padding(bottomPadding)
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Text("Trends", fontFamily = Heat.sairaCondensed, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = Heat.text)
        Spacer(Modifier.height(16.dp))

        // Totals (last 4 weeks)
        val last4 = weeks.takeLast(4)
        HeatCard(Modifier.fillMaxWidth(), radius = 18.dp) {
            Column {
                SectionLabel("LAST 4 WEEKS")
                Spacer(Modifier.height(11.dp))
                Row {
                    StatBlock(Fmt.dist(last4.sumOf { it.distM }, miles), "", valueSize = 31, unit = Fmt.distUnit(miles))
                    Spacer(Modifier.padding(horizontal = 12.dp))
                    StatBlock(Fmt.hoursShort(last4.sumOf { it.timeMs }), "", valueSize = 31, unit = "hr")
                    Spacer(Modifier.padding(horizontal = 12.dp))
                    StatBlock("${last4.sumOf { it.count }}", "", valueSize = 31, unit = "runs")
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // Weekly volume bars
        HeatCard(Modifier.fillMaxWidth()) {
            Column {
                SectionLabel("WEEKLY ${if (miles) "MILES" else "KM"}")
                Spacer(Modifier.height(14.dp))
                WeeklyBars(weeks, miles, Modifier.fillMaxWidth().height(120.dp))
            }
        }
        Spacer(Modifier.height(14.dp))

        // HRV trend
        HeatCard(Modifier.fillMaxWidth()) {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    SectionLabel("HRV · RMSSD", color = Heat.hrvLabel)
                    val avg = hrvPoints.takeIf { it.isNotEmpty() }?.map { it.second }?.average()
                    Text(
                        avg?.let { "avg ${it.roundToInt()} ms" } ?: "",
                        fontSize = 11.sp,
                        color = Heat.textDim,
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (hrvPoints.size >= 2) {
                    HrvLine(hrvPoints.map { it.second }, Modifier.fillMaxWidth().height(90.dp))
                } else {
                    Text("Not enough runs with HRV yet", fontSize = 12.sp, color = Heat.textFaint)
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

private data class WeekBucket(val label: String, val distM: Double, val timeMs: Long, val count: Int)

private fun weeklyBuckets(runs: List<RunSummaryEntity>, weeks: Int): List<WeekBucket> {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
    }
    val startOfThisWeek = cal.timeInMillis
    val weekMs = 7L * 86_400_000
    return (weeks - 1 downTo 0).map { back ->
        val from = startOfThisWeek - back * weekMs
        val to = from + weekMs
        val inWeek = runs.filter { it.startMs in from until to }
        val label = if (back == 0) "now" else "-$back"
        WeekBucket(label, inWeek.sumOf { it.distanceM }, inWeek.sumOf { it.movingMs }, inWeek.size)
    }
}

@Composable
private fun WeeklyBars(weeks: List<WeekBucket>, miles: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (weeks.isEmpty()) return@Canvas
        val maxDist = weeks.maxOf { it.distM }.coerceAtLeast(1.0)
        val gap = 8.dp.toPx()
        val barW = (size.width - gap * (weeks.size - 1)) / weeks.size
        weeks.forEachIndexed { i, w ->
            val h = ((w.distM / maxDist) * (size.height - 4.dp.toPx())).toFloat().coerceAtLeast(2.dp.toPx())
            val x = i * (barW + gap)
            drawRoundRect(
                brush = if (i == weeks.lastIndex) Brush.verticalGradient(listOf(Heat.brandOrange, Heat.brandRed))
                else Brush.verticalGradient(listOf(Heat.borderStrong, Heat.borderStrong)),
                topLeft = Offset(x, size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
        }
    }
}

@Composable
private fun HrvLine(values: List<Double>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val vMin = values.min() - 3
        val vMax = values.max() + 3
        val span = (vMax - vMin).coerceAtLeast(1.0)
        fun place(i: Int) = Offset(
            x = i.toFloat() / (values.size - 1) * size.width,
            y = ((vMax - values[i]) / span * size.height).toFloat(),
        )
        val p = Path()
        values.indices.forEach { i ->
            val o = place(i)
            if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y)
        }
        val fill = Path().apply {
            addPath(p); lineTo(size.width, size.height); lineTo(0f, size.height); close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(Heat.hrvPurple.copy(alpha = 0.28f), Heat.hrvPurple.copy(alpha = 0f))))
        drawPath(p, Heat.hrvPurple, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(Heat.hrvPurple, radius = 4.dp.toPx(), center = place(values.lastIndex))
    }
}
