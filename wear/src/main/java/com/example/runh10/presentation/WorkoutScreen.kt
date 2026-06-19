package com.example.runh10.presentation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.runh10.data.RunSettings
import com.example.runh10.presentation.components.ZoneRing
import com.example.runh10.presentation.components.zoneColor
import com.example.runh10.workout.RunState
import com.example.runh10.workout.ScanDevice
import com.example.runh10.workout.UiState
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Run H10 needs sensors, location & Bluetooth",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.onBackground,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRequest) { Text("Grant") }
    }
}

@Composable
fun WorkoutFlow(
    ui: UiState,
    devices: List<ScanDevice>,
    remembered: ScanDevice?,
    settings: RunSettings,
    onScan: () -> Unit,
    onPick: (String) -> Unit,
    onForget: () -> Unit,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onDone: () -> Unit,
    onPauseToggle: () -> Unit,
    onLap: () -> Unit,
    onStartNow: () -> Unit,
    onAge: (Int) -> Unit,
    onMaxHr: (Int) -> Unit,
    onMeasureResting: suspend () -> Int,
    onToggleAnnounce: (Boolean) -> Unit,
    onToggleAnnounceSplit: (Boolean) -> Unit,
    onToggleAnnouncePace: (Boolean) -> Unit,
    onToggleAnnounceZone: (Boolean) -> Unit,
    onToggleAutoPause: (Boolean) -> Unit,
) {
    var showSettings by remember { mutableStateOf(false) }
    var showSummary by remember { mutableStateOf(false) }
    var summaryUi by remember { mutableStateOf<UiState?>(null) }

    when {
        showSummary && summaryUi != null -> SummaryScreen(
            ui = summaryUi!!,
            onDone = onDone,
        )
        ui.running -> ActiveScreen(
            ui = ui,
            onPauseToggle = onPauseToggle,
            onLap = onLap,
            onStartNow = onStartNow,
            onEnd = {
                summaryUi = ui
                showSummary = true
                onEnd()
            },
        )
        showSettings -> SettingsScreen(
            settings = settings,
            onAge = onAge,
            onMaxHr = onMaxHr,
            onMeasureResting = onMeasureResting,
            onToggleAnnounce = onToggleAnnounce,
            onToggleAnnounceSplit = onToggleAnnounceSplit,
            onToggleAnnouncePace = onToggleAnnouncePace,
            onToggleAnnounceZone = onToggleAnnounceZone,
            onToggleAutoPause = onToggleAutoPause,
            onBack = { showSettings = false },
        )
        else -> PrepScreen(
            ui = ui,
            devices = devices,
            remembered = remembered,
            onScan = onScan,
            onPick = onPick,
            onForget = onForget,
            onStart = onStart,
            onSettings = { showSettings = true },
        )
    }
}

@Composable
private fun PrepScreen(
    ui: UiState,
    devices: List<ScanDevice>,
    remembered: ScanDevice?,
    onScan: () -> Unit,
    onPick: (String) -> Unit,
    onForget: () -> Unit,
    onStart: () -> Unit,
    onSettings: () -> Unit,
) {
    if (remembered != null) {
        // Remembered or just-picked device: show strap status, a big Start button
        // (enabled only once CONNECTED), and a secondary Change strap control.
        // The Prep screen persists here until the user taps Start — the run does
        // NOT begin until they do so.
        val isConnected = ui.hrState == "CONNECTED"
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (isConnected) "Connected to ${remembered.name}" else "Connecting to ${remembered.name}…",
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.primary,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onStart,
                enabled = isConnected,
            ) { Text("Start") }
            Spacer(Modifier.height(12.dp))
            Chip(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                onClick = onForget,
                colors = ChipDefaults.secondaryChipColors(),
                label = { Text("Change strap") },
            )
            Chip(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                onClick = onSettings,
                colors = ChipDefaults.secondaryChipColors(),
                label = { Text("Settings") },
            )
        }
    } else {
        // No remembered device: show scan UI.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Pair H10",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.primary,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onScan) { Text("Scan") }
            Spacer(Modifier.height(8.dp))
            if (devices.isEmpty()) {
                Text(
                    text = "No straps yet — tap Scan and wake the H10.",
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onBackground,
                )
            } else {
                devices.forEach { device ->
                    Chip(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        onClick = { onPick(device.address) },
                        colors = ChipDefaults.primaryChipColors(),
                        label = { Text(device.name) },
                        secondaryLabel = { Text("${device.rssi} dBm") },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Chip(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                onClick = onSettings,
                colors = ChipDefaults.secondaryChipColors(),
                label = { Text("Settings") },
            )
        }
    }
}

@Composable
private fun ActiveScreen(
    ui: UiState,
    onPauseToggle: () -> Unit,
    onLap: () -> Unit,
    onStartNow: () -> Unit,
    onEnd: () -> Unit,
) {
    val paused = ui.runState == RunState.AUTO_PAUSED || ui.runState == RunState.MANUAL_PAUSED
    val warmup = ui.runState == RunState.WARMUP
    val sweep = ui.hrZone?.let { it.toFloat() / 5f } ?: 0.2f

    Box(Modifier.fillMaxSize()) {
        // Edge ring — full screen overlay
        ZoneRing(
            zone = ui.hrZone,
            sweepFraction = sweep,
            dim = warmup,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // GPS lock indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            color = if (ui.gpsLocked) Color(0xFF34C759) else Color(0xFF666666),
                            shape = CircleShape,
                        ),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (ui.gpsLocked) "GPS" else "GPS…",
                    fontSize = 9.sp,
                    color = if (ui.gpsLocked) Color(0xFF34C759) else Color(0xFF666666),
                )
            }

            Spacer(Modifier.height(2.dp))

            // Status pill
            when (ui.runState) {
                RunState.WARMUP -> StatePill("WARMING UP", Color(0xFFFFCF6A))
                RunState.AUTO_PAUSED -> StatePill("AUTO-PAUSED", Color(0xFFFFCF6A))
                RunState.MANUAL_PAUSED -> StatePill("PAUSED", Color(0xFFC6A6FF))
                else -> Spacer(Modifier.height(16.dp))
            }

            Spacer(Modifier.height(4.dp))

            // Hero moving time
            Text(
                text = formatElapsed(ui.movingSec),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = if (paused) Color(0xFF4A4A4A) else Color.White,
            )
            Text(
                text = "MOVING TIME",
                fontSize = 9.sp,
                color = Color(0xFF666666),
                letterSpacing = 1.sp,
            )

            Spacer(Modifier.height(8.dp))

            // Metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                MetricCompact(
                    label = "mi",
                    value = formatMilesShort(ui.distanceMeters),
                    dim = paused,
                )
                MetricCompact(
                    label = "/mi",
                    value = if (warmup || ui.rollingPaceMps == null) "—" else formatPace(ui.rollingPaceMps),
                    dim = paused,
                )
                MetricCompact(
                    label = "bpm",
                    value = ui.bpm?.toString() ?: "—",
                    valueColor = zoneColor(ui.hrZone),
                    dim = false, // HR always bright
                )
            }

            Spacer(Modifier.height(10.dp))

            // Controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (warmup) {
                    ControlButton(label = "▶", onClick = onStartNow)
                } else {
                    ControlButton(
                        label = if (paused) "▶" else "⏸",
                        onClick = onPauseToggle,
                    )
                }
                ControlButton(label = "⚑", onClick = onLap)
                ControlButton(label = "■", onClick = onEnd, danger = true)
            }
        }
    }
}

@Composable
private fun StatePill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color = color.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun ControlButton(
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        colors = if (danger)
            ButtonDefaults.buttonColors(backgroundColor = Color(0xFF7A1010))
        else
            ButtonDefaults.secondaryButtonColors(),
    ) {
        Text(text = label, fontSize = 14.sp)
    }
}

@Composable
private fun MetricCompact(
    label: String,
    value: String,
    dim: Boolean = false,
    valueColor: Color? = null,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                valueColor != null -> valueColor
                dim -> Color(0xFF4A4A4A)
                else -> Color.White
            },
        )
        Text(
            text = label,
            fontSize = 9.sp,
            color = Color(0xFF666666),
        )
    }
}

// Keep the original Metric composable for any existing callers (PrepScreen debug rows etc.)
@Composable
private fun Metric(label: String, value: String, sub: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, fontSize = 13.sp, color = MaterialTheme.colors.onBackground)
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colors.onSurface,
            )
            if (sub != null) {
                Text(text = sub, fontSize = 10.sp, color = MaterialTheme.colors.onBackground)
            }
        }
    }
}

internal fun formatElapsed(sec: Long): String {
    val m = sec / 60
    val s = sec % 60
    return String.format(Locale.US, "%d:%02d", m, s)
}

internal fun formatMiles(meters: Double?): String {
    if (meters == null) return "—"
    return String.format(Locale.US, "%.2f mi", meters / 1609.344)
}

internal fun formatMilesShort(meters: Double?): String {
    if (meters == null) return "0.00"
    return String.format(Locale.US, "%.2f", meters / 1609.344)
}

internal fun formatPace(speedMps: Double?): String {
    if (speedMps == null || speedMps < 0.1) return "—"
    val secPerMile = (1609.344 / speedMps).roundToInt()
    val m = secPerMile / 60
    val s = secPerMile % 60
    return String.format(Locale.US, "%d:%02d", m, s)
}
