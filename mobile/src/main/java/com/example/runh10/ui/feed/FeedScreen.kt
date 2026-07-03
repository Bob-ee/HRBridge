package com.example.runh10.ui.feed

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.runh10.data.AthleteStore
import com.example.runh10.data.AthleteProfile
import com.example.runh10.data.RunJson
import com.example.runh10.data.RunRepository
import com.example.runh10.data.RunSummaryEntity
import com.example.runh10.ui.components.Fmt
import com.example.runh10.ui.components.HeatCard
import com.example.runh10.ui.components.HeatChip
import com.example.runh10.ui.components.PulseLogo
import com.example.runh10.ui.components.RouteHeatmap
import com.example.runh10.ui.components.SectionLabel
import com.example.runh10.ui.components.StatBlock
import com.example.runh10.ui.components.ZoneBar
import com.example.runh10.ui.theme.Heat
import kotlin.math.roundToInt

/** Home / Feed: this-week summary + run cards. */
@Composable
fun FeedScreen(
    onRunClick: (String) -> Unit,
    onProfileClick: () -> Unit,
    bottomPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    val context = LocalContext.current
    val repo = remember { RunRepository.get(context) }
    val athlete = remember { AthleteStore(context) }
    val runs = repo.observeAll().collectAsState(initial = emptyList()).value
    val week = repo.observeThisWeek().collectAsState(initial = emptyList()).value
    val profile = athlete.profile.collectAsState(initial = AthleteProfile()).value
    val miles = profile.unitsMiles

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Heat.bg),
        contentPadding = bottomPadding,
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulseLogo(24.dp)
                Spacer(Modifier.width(9.dp))
                Text(
                    text = "HR Bridge",
                    fontFamily = Heat.sairaCondensed,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 23.sp,
                    color = Heat.text,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(Heat.brandGradient, CircleShape)
                        .clickable(onClick = onProfileClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initials(profile.name),
                        fontFamily = Heat.sairaCondensed,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = Heat.text,
                    )
                }
            }
        }

        item {
            ThisWeekCard(week, miles, Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(16.dp))
        }

        if (runs.isEmpty()) {
            item {
                HeatCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No runs yet", fontFamily = Heat.sairaCondensed, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = Heat.text)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Record here or sync from your watch — runs land in this feed.",
                            fontSize = 12.sp, color = Heat.textMuted,
                        )
                    }
                }
            }
        } else {
            item {
                FeaturedRunCard(runs.first(), miles, Modifier.padding(horizontal = 20.dp)) { onRunClick(runs.first().sessionId) }
                Spacer(Modifier.height(14.dp))
            }
            items(runs.drop(1), key = { it.sessionId }) { run ->
                CompactRunCard(run, miles, Modifier.padding(horizontal = 20.dp)) { onRunClick(run.sessionId) }
                Spacer(Modifier.height(10.dp))
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

private fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
        .ifEmpty { "R" }

@Composable
private fun ThisWeekCard(week: List<RunSummaryEntity>, miles: Boolean, modifier: Modifier = Modifier) {
    val distM = week.sumOf { it.distanceM }
    val timeMs = week.sumOf { it.movingMs }
    val hrv = week.mapNotNull { it.hrvMs }.takeIf { it.isNotEmpty() }?.average()
    val zoneAgg = LongArray(5)
    week.forEach { r ->
        RunJson.decodeLongs(r.zoneMillisJson).forEachIndexed { i, ms -> if (i < 5) zoneAgg[i] += ms }
    }

    HeatCard(modifier.fillMaxWidth(), radius = 18.dp) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SectionLabel("THIS WEEK")
                Text("${week.size} run${if (week.size == 1) "" else "s"}", fontSize = 12.sp, color = Heat.textDim)
            }
            Spacer(Modifier.height(11.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                StatBlock(Fmt.dist(distM, miles), "", valueSize = 31, unit = Fmt.distUnit(miles))
                Spacer(Modifier.width(24.dp))
                StatBlock(Fmt.hoursShort(timeMs), "", valueSize = 31, unit = "hr")
                Spacer(Modifier.width(24.dp))
                StatBlock(hrv?.roundToInt()?.toString() ?: "—", "", valueSize = 31, unit = "ms HRV", valueColor = Heat.brandOrange)
            }
            Spacer(Modifier.height(14.dp))
            ZoneBar(zoneAgg.toList(), Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
private fun FeaturedRunCard(run: RunSummaryEntity, miles: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val route = remember(run.sessionId) { RunJson.decodePairs(run.routeJson) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Heat.surface)
            .border(1.dp, Heat.border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(148.dp)
                .background(
                    Brush.radialGradient(
                        listOf(androidx.compose.ui.graphics.Color(0xFF161D24), androidx.compose.ui.graphics.Color(0xFF0C1014)),
                    ),
                ),
        ) {
            RouteHeatmap(route, Modifier.fillMaxSize(), showGrid = false)
            HeatChip(
                run.workoutType,
                Heat.brandOrange,
                Modifier.padding(12.dp),
            )
        }
        Column(Modifier.padding(horizontal = 17.dp, vertical = 15.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text(
                    text = run.name,
                    fontFamily = Heat.sairaCondensed,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = Heat.text,
                )
                Text(Fmt.whenLabel(run.startMs), fontSize = 12.sp, color = Heat.textDim)
            }
            Spacer(Modifier.height(13.dp))
            Row(Modifier.fillMaxWidth()) {
                StatBlock(Fmt.dist(run.distanceM, miles), "DISTANCE", valueSize = 26, unit = Fmt.distUnit(miles), modifier = Modifier.weight(1f))
                StatBlock(
                    Fmt.pace(if (run.movingMs > 0) run.distanceM / (run.movingMs / 1000.0) else null, miles),
                    "AVG PACE", valueSize = 26, unit = Fmt.paceUnit(miles), modifier = Modifier.weight(1f),
                )
                StatBlock(
                    run.avgBpm?.toString() ?: "—", "AVG HR", valueSize = 26, unit = "bpm",
                    valueColor = Heat.brandOrange, modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CompactRunCard(run: RunSummaryEntity, miles: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val route = remember(run.sessionId) { RunJson.decodePairs(run.routeJson) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Heat.surface)
            .border(1.dp, Heat.border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 90.dp, height = 70.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Heat.surfaceDeep),
        ) {
            RouteHeatmap(route, Modifier.fillMaxSize(), strokeWidthDp = 3f, showGrid = false, showEndpoints = false)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = run.name,
                fontFamily = Heat.sairaCondensed,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = Heat.text,
            )
            Spacer(Modifier.height(2.dp))
            val pace = Fmt.pace(if (run.movingMs > 0) run.distanceM / (run.movingMs / 1000.0) else null, miles)
            Text(
                text = "${Fmt.whenLabel(run.startMs).substringBefore(" ·")} · ${Fmt.dist(run.distanceM, miles)} ${Fmt.distUnit(miles)} · $pace ${Fmt.paceUnit(miles)} · ${run.avgBpm ?: "—"} bpm",
                fontSize = 12.sp,
                color = Heat.textDim,
            )
        }
    }
}
