package com.example.runh10.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.runh10.data.AthleteProfile
import com.example.runh10.data.AthleteStore
import com.example.runh10.data.RunJson
import com.example.runh10.data.RunRepository
import com.example.runh10.shared.zones.ZoneCalculator
import com.example.runh10.ui.components.Fmt
import com.example.runh10.ui.components.HeatCard
import com.example.runh10.ui.components.SectionLabel
import com.example.runh10.ui.theme.Heat
import java.util.Calendar
import kotlin.math.roundToInt

/** Profile: athlete header, lifetime stats, heart profile, %HRR zones, PRs. */
@Composable
fun ProfileScreen(
    onEditHeart: () -> Unit,
    bottomPadding: PaddingValues,
) {
    val context = LocalContext.current
    val repo = remember { RunRepository.get(context) }
    val profile = remember { AthleteStore(context) }.profile
        .collectAsState(initial = AthleteProfile()).value
    val runs = repo.observeAll().collectAsState(initial = emptyList()).value
    val miles = profile.unitsMiles

    val thisYear = Calendar.getInstance().get(Calendar.YEAR)
    val runsThisYear = runs.count {
        Calendar.getInstance().apply { timeInMillis = it.startMs }.get(Calendar.YEAR) == thisYear
    }
    val lifetimeDist = runs.sumOf { it.distanceM }
    val fastestMile = runs.flatMap { RunJson.decodeSplits(it.splitsJson) }
        .filter { it.distanceM > 1500 }
        .maxByOrNull { it.avgPaceMps }
    val longest = runs.maxOfOrNull { it.distanceM }
    val bestHrv = runs.mapNotNull { it.hrvMs }.maxOrNull()

    Column(
        Modifier
            .fillMaxSize()
            .background(Heat.bg)
            .verticalScroll(rememberScrollState())
            .padding(bottomPadding),
    ) {
        // Athlete header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(68.dp).background(Heat.brandGradient, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    profile.name.trim().split(Regex("\\s+")).take(2)
                        .mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("").ifEmpty { "R" },
                    fontFamily = Heat.sairaCondensed,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp,
                    color = Color.White,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    profile.name,
                    fontFamily = Heat.sairaCondensed,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    color = Heat.text,
                )
                Text(
                    profile.subtitle.ifEmpty { "Edit your profile in Settings" },
                    fontSize = 13.sp,
                    color = Heat.textDim,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        // Lifetime stats
        HeatCard(Modifier.fillMaxWidth().padding(horizontal = 24.dp), radius = 18.dp, padding = 16.dp) {
            Row(Modifier.fillMaxWidth()) {
                LifetimeStat("${runs.size}", "RUNS", Modifier.weight(1f))
                LifetimeStat(Fmt.dist(lifetimeDist, miles).substringBefore('.'), if (miles) "MILES" else "KM", Modifier.weight(1f))
                LifetimeStat("$runsThisYear", "THIS YEAR", Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(16.dp))

        // Heart profile
        HeatCard(Modifier.fillMaxWidth().padding(horizontal = 24.dp), padding = 18.dp) {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SectionLabel("HEART PROFILE")
                    Text(
                        "EDIT",
                        fontFamily = Heat.sairaCondensed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Heat.brandOrange,
                        modifier = Modifier.clickable(onClick = onEditHeart),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    HeartWell(
                        value = profile.maxHr?.toString() ?: "—",
                        color = Heat.danger,
                        label = "MAX HR",
                        modifier = Modifier.weight(1f),
                    )
                    HeartWell(
                        value = profile.restingHr?.toString() ?: "—",
                        color = Heat.infoBlue,
                        label = "RESTING" + (profile.restingMeasuredAtMs?.let { " · measured" } ?: ""),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // Zones
        HeatCard(Modifier.fillMaxWidth().padding(horizontal = 24.dp), padding = 18.dp) {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    SectionLabel("YOUR ZONES")
                    Text("% heart-rate reserve", fontSize = 11.sp, color = Heat.textDim)
                }
                Spacer(Modifier.height(14.dp))
                val calc = profile.maxHr?.let { mx -> profile.restingHr?.let { r -> ZoneCalculator(mx, r) } }
                if (calc == null) {
                    Text(
                        "Set your max & resting HR to derive zones",
                        fontSize = 13.sp,
                        color = Heat.textDim,
                    )
                } else {
                    val ranges = listOf("50–60% HRR", "60–70% HRR", "70–80% HRR", "80–90% HRR", "90–100% HRR")
                    val edges = calc.edges
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        for (z in 1..5) {
                            val band = when (z) {
                                1 -> "<${edges[1]}"
                                5 -> "${edges[4]}+"
                                else -> "${edges[z - 1] + if (z == 2) 0 else 1}–${edges[z]}"
                            }
                            ZoneRow(z, Heat.zoneNames[z - 1], ranges[z - 1], band)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // PRs
        Column(Modifier.padding(horizontal = 24.dp)) {
            SectionLabel("PERSONAL RECORDS")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrCard(fastestMile?.let { Fmt.pace(it.avgPaceMps, miles) } ?: "—", "FASTEST ${if (miles) "MILE" else "KM"}", Heat.text, Modifier.weight(1f))
                PrCard(longest?.let { Fmt.dist(it, miles) } ?: "—", "LONGEST · ${Fmt.distUnit(miles)}", Heat.text, Modifier.weight(1f))
                PrCard(bestHrv?.roundToInt()?.toString() ?: "—", "BEST HRV · ms", Heat.hrvPurple, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(26.dp))
    }
}

@Composable
private fun LifetimeStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = Heat.sairaCondensed, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = Heat.text)
        Text(label, fontSize = 10.sp, letterSpacing = 0.8.sp, color = Heat.textDim, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun HeartWell(value: String, color: Color, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Heat.surfaceDeep)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontFamily = Heat.sairaCondensed, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, color = color)
            Text("bpm", fontSize = 12.sp, color = Heat.textMuted, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        }
        Text(label, fontSize = 11.sp, letterSpacing = 0.7.sp, color = Heat.textDim, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ZoneRow(z: Int, name: String, range: String, band: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(34.dp)
                .background(Heat.zones[z - 1].copy(alpha = 0.18f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("Z$z", fontFamily = Heat.sairaCondensed, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Heat.zones[z - 1])
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontFamily = Heat.sairaCondensed, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Heat.text)
            Text(range, fontSize = 11.sp, color = Heat.textDim)
        }
        Text(band, fontFamily = Heat.sairaCondensed, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Heat.textMuted)
    }
}

@Composable
private fun PrCard(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    HeatCard(modifier, radius = 14.dp, padding = 14.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontFamily = Heat.sairaCondensed, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, color = color)
            Text(label, fontSize = 10.sp, letterSpacing = 0.6.sp, color = Heat.textDim, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
