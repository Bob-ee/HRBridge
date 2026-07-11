package com.example.runh10.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.runh10.data.AthleteProfile
import com.example.runh10.data.AthleteStore
import com.example.runh10.data.RunJson
import com.example.runh10.data.RunRepository
import com.example.runh10.data.SplitJson
import com.example.runh10.ui.components.Fmt
import com.example.runh10.ui.components.HeatCard
import com.example.runh10.ui.components.HrCurveChart
import com.example.runh10.ui.components.RouteHeatmap
import com.example.runh10.ui.components.SectionLabel
import com.example.runh10.ui.components.SplitBar
import com.example.runh10.ui.components.ZoneDonut
import com.example.runh10.ui.theme.Heat
import kotlin.math.roundToInt

/** Full analysis of one run — map hero, effort, HR curve, splits. */
@Composable
fun RunDetailScreen(sessionId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { RunRepository.get(context) }
    val run = repo.observeById(sessionId).collectAsState(initial = null).value ?: return
    val profile = remember { AthleteStore(context) }.profile.collectAsState(initial = AthleteProfile()).value
    val miles = profile.unitsMiles

    val route = remember(run.sessionId) { RunJson.decodePairs(run.routeJson) }
    val hrSeries = remember(run.sessionId) { RunJson.decodePairs(run.hrSeriesJson) }
    val zoneMillis = remember(run.sessionId) { RunJson.decodeLongs(run.zoneMillisJson) }
    val splits = remember(run.sessionId) { RunJson.decodeSplits(run.splitsJson) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Heat.bg)
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Map hero ──
        Box(
            Modifier
                .fillMaxWidth()
                .height(312.dp)
                .background(Brush.radialGradient(listOf(Color(0xFF18222B), Color(0xFF0A0E12)))),
        ) {
            RouteHeatmap(route, Modifier.fillMaxSize(), strokeWidthDp = 6f)
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(top = 6.dp, start = 20.dp, end = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    Modifier
                        .size(42.dp)
                        .background(Color(0xA00A0C0F), CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("‹", fontSize = 26.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
                // Heat legend
                Row(
                    Modifier
                        .background(Color(0xA00A0C0F), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("EASY", fontSize = 9.sp, letterSpacing = 1.sp, color = Heat.textMuted)
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .width(46.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Brush.horizontalGradient(Heat.heatGradientColors)),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("HARD", fontSize = 9.sp, letterSpacing = 1.sp, color = Heat.textMuted)
                }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 18.dp)) {
                com.example.runh10.ui.components.HeatChip(
                    "${Fmt.chipDate(run.startMs)} · ${run.workoutType}",
                    Heat.brandOrange,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = run.name,
                    fontFamily = Heat.sairaCondensed,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp,
                    color = Heat.text,
                )
                Text(
                    text = "${run.source.replaceFirstChar { it.uppercase() }} recorded · ${(run.elevGainM * 3.28084).roundToInt()} ft gain",
                    fontSize = 13.sp,
                    color = Heat.textMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(20.dp))

            // ── Primary 3-up ──
            Row(Modifier.fillMaxWidth()) {
                PrimaryStat(Fmt.dist(run.distanceM, miles), if (miles) "MILES" else "KM", Modifier.weight(1f))
                PrimaryStat(Fmt.duration(run.movingMs / 1000), "TIME", Modifier.weight(1f), hairline = true)
                PrimaryStat(
                    Fmt.pace(if (run.movingMs > 0) run.distanceM / (run.movingMs / 1000.0) else null, miles),
                    if (miles) "/MILE" else "/KM", Modifier.weight(1f), hairline = true,
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Effort: zones donut + HRV / HR cards ──
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeatCard(Modifier.weight(1f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        SectionLabel("TIME IN ZONES", modifier = Modifier.align(Alignment.Start))
                        Spacer(Modifier.height(14.dp))
                        val total = zoneMillis.sum().coerceAtLeast(1)
                        val dominant = zoneMillis.indices.maxByOrNull { zoneMillis[it] } ?: 0
                        Box(Modifier.size(108.dp), contentAlignment = Alignment.Center) {
                            ZoneDonut(zoneMillis, Modifier.fillMaxSize())
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Z${dominant + 1}",
                                    fontFamily = Heat.sairaCondensed,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 22.sp,
                                    color = Heat.zones[dominant],
                                )
                                val pct = (zoneMillis[dominant] * 100 / total).toInt()
                                val mins = zoneMillis[dominant] / 60_000
                                Text("$pct% · ${mins}m", fontSize = 10.sp, color = Heat.textMuted)
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // HRV card (purple gradient)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Brush.linearGradient(listOf(Heat.hrvCardTop, Heat.surface)))
                            .border(1.dp, Heat.hrvBorder, RoundedCornerShape(20.dp))
                            .padding(15.dp),
                    ) {
                        Column {
                            SectionLabel("HRV · RMSSD", color = Heat.hrvLabel)
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    run.hrvMs?.roundToInt()?.toString() ?: "—",
                                    fontFamily = Heat.sairaCondensed,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 30.sp,
                                    color = Heat.hrvPurple,
                                )
                                Text("ms", fontSize = 12.sp, color = Heat.textMuted, modifier = Modifier.padding(start = 3.dp, bottom = 3.dp))
                            }
                        }
                    }
                    HeatCard(Modifier.fillMaxWidth(), padding = 15.dp) {
                        Column {
                            KvRow("Avg HR", run.avgBpm?.toString() ?: "—", Heat.brandOrange)
                            Spacer(Modifier.height(5.dp))
                            KvRow("Calories", run.kcal?.roundToInt()?.toString() ?: "—", Heat.text)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── HR curve ──
            HeatCard(Modifier.fillMaxWidth()) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SectionLabel("HEART RATE")
                        Text(
                            text = run.maxBpm?.let { "$it MAX" } ?: "",
                            fontFamily = Heat.sairaCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Heat.danger,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (hrSeries.size >= 2) {
                        HrCurveChart(hrSeries, Modifier.fillMaxWidth().height(80.dp))
                    } else {
                        Text("No heart-rate data", fontSize = 12.sp, color = Heat.textFaint)
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── Splits ──
            if (splits.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text(
                        "Splits",
                        fontFamily = Heat.sairaCondensed,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = Heat.text,
                    )
                    Text(if (miles) "per mile" else "per km", fontSize = 11.sp, color = Heat.textDim)
                }
                Spacer(Modifier.height(10.dp))
                val fastest = splits.maxByOrNull { it.avgPaceMps }
                val maxPace = (fastest?.avgPaceMps ?: 1.0).coerceAtLeast(0.1)
                splits.forEach { s ->
                    SplitRow(s, s === fastest, (s.avgPaceMps / maxPace).toFloat(), miles)
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(90.dp))
        }
    }
}

@Composable
private fun PrimaryStat(value: String, label: String, modifier: Modifier = Modifier, hairline: Boolean = false) {
    Row(modifier) {
        if (hairline) {
            Box(Modifier.width(1.dp).height(44.dp).background(Heat.border))
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value,
                fontFamily = Heat.sairaCondensed,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 30.sp,
                color = Heat.text,
            )
            Text(label, fontSize = 11.sp, letterSpacing = 1.sp, color = Heat.textDim, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun KvRow(k: String, v: String, vColor: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text(k, fontSize = 12.sp, color = Heat.textMuted)
        Text(v, fontFamily = Heat.sairaCondensed, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, color = vColor)
    }
}

@Composable
private fun SplitRow(s: SplitJson, highlight: Boolean, frac: Float, miles: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (highlight) Color(0xFF15110C) else Heat.surface)
            .border(1.dp, if (highlight) Heat.brandOrange.copy(alpha = 0.25f) else Heat.border, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${s.index}",
            fontFamily = Heat.sairaCondensed,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            color = if (highlight) Heat.brandOrange else Heat.text,
            modifier = Modifier.width(20.dp),
        )
        SplitBar(frac, Modifier.weight(1f).height(8.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            Fmt.pace(s.avgPaceMps, miles),
            fontFamily = Heat.sairaCondensed,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            color = if (highlight) Heat.brandOrange else Heat.text,
            modifier = Modifier.width(48.dp),
        )
        Text(
            s.avgBpm?.toString() ?: "—",
            fontSize = 12.sp,
            color = Heat.textMuted,
            modifier = Modifier.width(34.dp),
        )
    }
}
