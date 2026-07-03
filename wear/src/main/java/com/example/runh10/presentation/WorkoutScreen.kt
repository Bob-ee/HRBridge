package com.example.runh10.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.example.runh10.data.RunSettings
import com.example.runh10.media.WatchMediaClient
import com.example.runh10.presentation.theme.Heat
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
            .background(Heat.bg)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "HR Bridge needs sensors, location & Bluetooth",
            textAlign = TextAlign.Center,
            fontSize = 13.sp,
            color = Heat.text,
        )
        Spacer(Modifier.height(14.dp))
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .background(Heat.brandGradient, RoundedCornerShape(16.dp))
                .clickable(onClick = onRequest)
                .padding(horizontal = 26.dp, vertical = 10.dp),
        ) {
            Text(
                text = "GRANT",
                fontFamily = Heat.sairaCondensed,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                letterSpacing = 1.4.sp,
                color = Heat.text,
            )
        }
    }
}

@Composable
fun WorkoutFlow(
    ui: UiState,
    devices: List<ScanDevice>,
    remembered: ScanDevice?,
    settings: RunSettings,
    media: WatchMediaClient,
    ambientState: AmbientState = AmbientState(),
    onScan: () -> Unit,
    onPick: (String) -> Unit,
    onForget: () -> Unit,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onDone: () -> Unit,
    onPauseToggle: () -> Unit,
    onStartNow: () -> Unit,
    onLap: () -> Unit,
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
        ui.running -> RunExperience(
            ui = ui,
            ambientState = ambientState,
            media = media,
            onPauseToggle = onPauseToggle,
            onStartNow = onStartNow,
            onLap = onLap,
            onFinish = {
                summaryUi = ui
                showSummary = true
                onEnd()
            },
        )
        showSettings -> SettingsScreen(
            settings = settings,
            strapName = remembered?.name,
            strapConnected = ui.hrState == "CONNECTED",
            onForgetStrap = {
                showSettings = false
                onForget()
            },
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
        else -> ReadyScreen(
            ui = ui,
            devices = devices,
            remembered = remembered,
            onScan = onScan,
            onPick = onPick,
            onStart = onStart,
            onSettings = { showSettings = true },
        )
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
