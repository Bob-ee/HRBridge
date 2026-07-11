package com.example.runh10.ui.record

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.runh10.data.AthleteProfile
import com.example.runh10.data.AthleteStore
import com.example.runh10.record.PhoneRecordController
import com.example.runh10.record.PhoneRunUi
import com.example.runh10.record.RecordForegroundService
import com.example.runh10.shared.run.RunState
import com.example.runh10.ui.components.EkgTrace
import com.example.runh10.ui.components.Fmt
import com.example.runh10.ui.components.GradientButton
import com.example.runh10.ui.components.HeatCard
import com.example.runh10.ui.components.HeatChip
import com.example.runh10.ui.components.RouteHeatmap
import com.example.runh10.ui.components.SectionLabel
import com.example.runh10.ui.theme.Heat
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val recordPermissions = arrayOf(
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACTIVITY_RECOGNITION,
    Manifest.permission.POST_NOTIFICATIONS,
)

/** The record loop: Ready → Live → Save, one route. */
@Composable
fun RecordScreen(onClose: () -> Unit, onSaved: (String) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { PhoneRecordController.init(context.applicationContext) }
    val ui by PhoneRecordController.ui.collectAsState()
    val profile = remember { AthleteStore(context) }.profile.collectAsState(initial = AthleteProfile()).value

    when (ui.phase) {
        PhoneRunUi.Phase.READY -> ReadyContent(ui, profile, onClose)
        PhoneRunUi.Phase.LIVE -> LiveContent(ui, profile)
        PhoneRunUi.Phase.SAVE -> SaveContent(ui, profile, onSaved, onClose)
    }
}

// ───────────────────────── Ready ─────────────────────────

@Composable
private fun ReadyContent(ui: PhoneRunUi, profile: AthleteProfile, onClose: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            recordPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            },
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = recordPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(recordPermissions) }

    val remembered by PhoneRecordController.rememberedDevice.collectAsState()
    val devices by PhoneRecordController.devices.collectAsState()
    val connected = ui.bleState == "CONNECTED"

    // Auto-connect to the remembered strap once permissions are in.
    var autoConnected by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(remembered, granted) {
        val r = remembered
        if (granted && !autoConnected && r != null) {
            autoConnected = true
            PhoneRecordController.connectStrap(r.address, autoConnect = true)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Heat.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹",
                fontSize = 26.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onClose).padding(end = 12.dp),
            )
            Text(
                "Ready to run",
                fontFamily = Heat.sairaCondensed,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp,
                color = Heat.text,
            )
        }
        Spacer(Modifier.height(18.dp))

        // Workout type
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("RUN", "TRAIL", "TRACK").forEach { t ->
                val active = ui.workoutType == t
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Heat.surface)
                        .border(
                            if (active) 1.5.dp else 1.dp,
                            if (active) Heat.brandOrange else Heat.border,
                            RoundedCornerShape(14.dp),
                        )
                        .clickable { PhoneRecordController.setWorkoutType(t) }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        t,
                        fontFamily = Heat.sairaCondensed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 0.6.sp,
                        color = if (active) Heat.brandOrange else Heat.textDim,
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))

        // Sensor card
        HeatCard(Modifier.fillMaxWidth(), padding = 18.dp) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .background(Heat.brandRed.copy(alpha = 0.12f), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center,
                    ) { com.example.runh10.ui.components.PulseLogo(24.dp) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            remembered?.name ?: "Polar H10",
                            fontFamily = Heat.sairaCondensed,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = Heat.text,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                            Box(
                                Modifier.size(7.dp).background(
                                    if (connected) Heat.goodGreen else Color(0xFFFFCF6A),
                                    CircleShape,
                                ),
                            )
                            Spacer(Modifier.width(7.dp))
                            val batterySuffix = ui.batteryPct?.takeIf { connected }?.let { " · $it%" } ?: ""
                            Text(
                                when {
                                    connected -> "Connected"
                                    remembered != null -> "Connecting…"
                                    else -> "Not paired"
                                } + batterySuffix,
                                fontSize = 12.sp,
                                color = if (connected) Heat.goodGreen else Heat.textDim,
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            ui.bpm?.toString() ?: "—",
                            fontFamily = Heat.sairaCondensed,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 26.sp,
                            color = if (ui.hrStale) Heat.brandRed.copy(alpha = 0.4f) else Heat.brandRed,
                        )
                        Text("LIVE", fontSize = 10.sp, letterSpacing = 0.8.sp, color = Heat.textDim)
                    }
                }
                if (connected) {
                    Spacer(Modifier.height(12.dp))
                    EkgTrace(Modifier.fillMaxWidth().height(30.dp))
                }
                if (remembered == null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "SCAN FOR STRAPS",
                        fontFamily = Heat.sairaCondensed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.4.sp,
                        color = Heat.infoBlue,
                        modifier = Modifier.clickable { PhoneRecordController.startScan() },
                    )
                    devices.forEach { d ->
                        Text(
                            "· ${d.name}  (${d.rssi} dBm)",
                            fontSize = 13.sp,
                            color = Heat.text,
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .clickable { PhoneRecordController.connectStrap(d.address, autoConnect = false) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // GPS card
        HeatCard(Modifier.fillMaxWidth(), padding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).background(Heat.infoBlue.copy(alpha = 0.12f), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("📍", fontSize = 18.sp) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("GPS", fontFamily = Heat.sairaCondensed, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Heat.text)
                    Text(
                        when {
                            ui.gpsLocked -> "Locked · high accuracy"
                            ui.gpsAccuracyM != null -> "Acquiring · ±${ui.gpsAccuracyM.roundToInt()} m"
                            else -> "Starts with the run"
                        },
                        fontSize = 12.sp,
                        color = if (ui.gpsLocked) Heat.goodGreen else Heat.textDim,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                SignalBars(if (ui.gpsLocked) 3 else if (ui.gpsAccuracyM != null) 2 else 0)
            }
        }

        Spacer(Modifier.height(34.dp))

        // START
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val canStart = granted && connected
            Box(
                Modifier
                    .size(128.dp)
                    .alpha(if (canStart) 1f else 0.45f)
                    .background(Heat.brandGradient, CircleShape)
                    .clickable(enabled = canStart) {
                        RecordForegroundService.start(context)
                        PhoneRecordController.beginRun(
                            autoPause = profile.autoPause,
                            voiceCoach = profile.voiceCoach,
                            mileAnnouncements = profile.mileAnnouncements,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "START",
                    fontFamily = Heat.sairaCondensed,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 30.sp,
                    letterSpacing = 1.8.sp,
                    color = Color.White,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                if (profile.autoPause) "Auto-pauses when you stop moving" else "Warmup ends when you start moving",
                fontSize = 12.sp,
                color = Heat.textDim,
            )
            Spacer(Modifier.height(110.dp))
        }
    }
}

@Composable
private fun SignalBars(level: Int) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf(8, 13, 18).forEachIndexed { i, h ->
            Box(
                Modifier
                    .width(4.dp)
                    .height(h.dp)
                    .background(
                        if (i < level) Heat.goodGreen else Heat.borderStrong,
                        RoundedCornerShape(1.dp),
                    ),
            )
        }
    }
}

// ───────────────────────── Live ─────────────────────────

@Composable
private fun LiveContent(ui: PhoneRunUi, profile: AthleteProfile) {
    val context = LocalContext.current
    var locked by remember { mutableStateOf(false) }
    val paused = ui.runState == RunState.MANUAL_PAUSED || ui.runState == RunState.AUTO_PAUSED
    val warmup = ui.runState == RunState.WARMUP
    val miles = profile.unitsMiles

    Column(
        Modifier.fillMaxSize().background(Heat.bg).statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        // Recording status
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(Heat.brandRed, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    warmup -> "WARMING UP"
                    paused -> "PAUSED"
                    else -> "RECORDING"
                },
                fontFamily = Heat.sairaCondensed,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.8.sp,
                color = Heat.brandRed,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "· H10 ${if (ui.bleState == "CONNECTED") "●●●" else "○○○"} · GPS ${if (ui.gpsLocked) "●●●" else "○○○"}",
                fontSize = 12.sp,
                color = Heat.textDim,
            )
        }

        // Clock
        Spacer(Modifier.height(8.dp))
        Text(
            Fmt.duration(ui.elapsedSec),
            fontFamily = Heat.sairaCondensed,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 60.sp,
            color = Heat.text,
        )
        Text(
            "ELAPSED · MOVING ${Fmt.duration(ui.movingSec)}",
            fontSize = 11.sp,
            letterSpacing = 1.4.sp,
            color = Heat.textDim,
        )

        Spacer(Modifier.height(16.dp))

        // Zone ring
        Box(Modifier.size(212.dp), contentAlignment = Alignment.Center) {
            com.example.runh10.ui.components.PhoneZoneRing(
                zone = ui.hrZone,
                bpm = ui.bpm,
                zoneEdges = ui.zoneEdges,
                modifier = Modifier.fillMaxSize(),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    ui.bpm?.toString() ?: "––",
                    fontFamily = Heat.sairaCondensed,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 72.sp,
                    lineHeight = 64.sp,
                    color = if (ui.hrStale) Heat.zoneColor(ui.hrZone).copy(alpha = 0.4f) else Heat.zoneColor(ui.hrZone),
                )
                Text("BPM", fontSize = 12.sp, letterSpacing = 1.4.sp, color = Heat.textMuted)
                Spacer(Modifier.height(8.dp))
                HeatChip(
                    if (ui.hrZone != null) "ZONE ${ui.hrZone} · ${Heat.zoneName(ui.hrZone).uppercase()}"
                    else "SET YOUR HEART PROFILE",
                    Heat.zoneColor(ui.hrZone),
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // 2×2 grid
        HeatCard(Modifier.fillMaxWidth().padding(horizontal = 24.dp), radius = 18.dp, padding = 0.dp, background = Heat.bg) {
            Column {
                Row(Modifier.fillMaxWidth().height(76.dp)) {
                    GridCell(Fmt.dist(ui.distanceM, miles), Fmt.distUnit(miles), "DISTANCE", Modifier.weight(1f))
                    Box(Modifier.width(1.dp).height(76.dp).background(Heat.border))
                    GridCell(Fmt.pace(ui.rollingPaceMps, miles), Fmt.paceUnit(miles), "CURRENT PACE", Modifier.weight(1f))
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Heat.border))
                Row(Modifier.fillMaxWidth().height(76.dp)) {
                    GridCell(Fmt.pace(ui.avgPaceMps, miles), Fmt.paceUnit(miles), "AVG PACE", Modifier.weight(1f))
                    Box(Modifier.width(1.dp).height(76.dp).background(Heat.border))
                    GridCell(ui.cadenceSpm?.roundToInt()?.toString() ?: "—", "spm", "CADENCE", Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Controls
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(30.dp)) {
            ControlCircleSmall(if (locked) "🔒" else "🔓") { locked = !locked }
            Box(
                Modifier
                    .size(92.dp)
                    .alpha(if (locked) 0.4f else 1f)
                    .background(Heat.brandGradient, CircleShape)
                    .clickable(enabled = !locked) {
                        if (warmup) PhoneRecordController.startNow()
                        else if (paused) PhoneRecordController.manualResume()
                        else PhoneRecordController.manualPause()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (warmup) "GO" else if (paused) "▶" else "❚❚",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = Heat.sairaCondensed,
                )
            }
            Box(
                Modifier
                    .size(60.dp)
                    .alpha(if (locked) 0.4f else 1f)
                    .background(Color(0xFF15110C), CircleShape)
                    .border(1.5.dp, Color(0xFF43242A), CircleShape)
                    .clickable(enabled = !locked) {
                        PhoneRecordController.finishRun()
                        RecordForegroundService.stop(context)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(20.dp).background(Heat.danger, RoundedCornerShape(4.dp)))
            }
        }
        Spacer(Modifier.height(38.dp))
    }
}

@Composable
private fun ControlCircleSmall(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(60.dp)
            .background(Heat.surface, CircleShape)
            .border(1.5.dp, Heat.borderStrong, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(glyph, fontSize = 20.sp) }
}

@Composable
private fun GridCell(value: String, unit: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 18.dp, vertical = 15.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                fontFamily = Heat.sairaCondensed,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp,
                lineHeight = 28.sp,
                color = Heat.text,
            )
            Text(unit, fontSize = 13.sp, color = Heat.textMuted, modifier = Modifier.padding(start = 3.dp, bottom = 2.dp))
        }
        Text(label, fontSize = 11.sp, letterSpacing = 1.sp, color = Heat.textDim, modifier = Modifier.padding(top = 3.dp))
    }
}

// ───────────────────────── Save ─────────────────────────

@Composable
private fun SaveContent(
    ui: PhoneRunUi,
    profile: AthleteProfile,
    onSaved: (String) -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val miles = profile.unitsMiles
    var name by rememberSaveable { mutableStateOf(defaultName()) }
    var feel by rememberSaveable { mutableStateOf<String?>("steady") }

    Column(
        Modifier
            .fillMaxSize()
            .background(Heat.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Nice run! 🔥", fontFamily = Heat.sairaCondensed, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, color = Heat.text)
        Text(
            "Saving to your feed · Health Connect",
            fontSize = 13.sp,
            color = Heat.textDim,
            modifier = Modifier.padding(top = 5.dp),
        )
        Spacer(Modifier.height(18.dp))

        // Summary card
        HeatCard(Modifier.fillMaxWidth(), padding = 0.dp) {
            Column {
                Box(
                    Modifier.fillMaxWidth().height(128.dp).clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(Heat.surfaceDeep),
                ) {
                    RouteHeatmap(ui.routePoints.map { listOf(it.first, it.second) }, Modifier.fillMaxSize(), showGrid = false)
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp)) {
                    SaveStat(Fmt.dist(ui.distanceM, miles), if (miles) "MILES" else "KM", Heat.text, Modifier.weight(1f))
                    SaveStat(Fmt.duration(ui.movingSec), "TIME", Heat.text, Modifier.weight(1f))
                    SaveStat(ui.avgBpm?.toString() ?: "—", "AVG HR", Heat.brandOrange, Modifier.weight(1f))
                    SaveStat(ui.hrvMs?.roundToInt()?.toString() ?: "—", "HRV ms", Heat.hrvPurple, Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        SectionLabel("RUN NAME")
        Spacer(Modifier.height(7.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Heat.surface)
                .border(1.dp, Heat.borderStrong, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                textStyle = TextStyle(
                    fontFamily = Heat.sairaCondensed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Heat.text,
                ),
                singleLine = true,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Heat.brandOrange),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(14.dp))

        SectionLabel("HOW DID IT FEEL?")
        Spacer(Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("easy" to "Easy", "steady" to "Steady", "hard" to "Hard", "max" to "Max").forEach { (key, label) ->
                val active = feel == key
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) Heat.brandOrange.copy(alpha = 0.14f) else Heat.surface)
                        .border(
                            if (active) 1.5.dp else 1.dp,
                            if (active) Heat.brandOrange else Heat.border,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { feel = key }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        fontFamily = Heat.sairaCondensed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (active) Heat.brandOrange else Heat.textDim,
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        GradientButton("SAVE RUN", onClick = {
            scope.launch {
                val id = PhoneRecordController.saveRun(name, feel)
                if (id != null) onSaved(id) else onClose()
            }
        }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Text(
            "DISCARD",
            fontFamily = Heat.sairaCondensed,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            letterSpacing = 1.4.sp,
            color = Heat.textDim,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable {
                    PhoneRecordController.discardRun()
                    onClose()
                }
                .padding(8.dp),
        )
        Spacer(Modifier.height(100.dp))
    }
}

@Composable
private fun SaveStat(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = Heat.sairaCondensed, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = color)
        Text(label, fontSize = 10.sp, letterSpacing = 0.8.sp, color = Heat.textDim, modifier = Modifier.padding(top = 3.dp))
    }
}

private fun defaultName(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 4..11 -> "Morning Run"
        in 12..16 -> "Afternoon Run"
        in 17..20 -> "Evening Run"
        else -> "Night Run"
    }
}
