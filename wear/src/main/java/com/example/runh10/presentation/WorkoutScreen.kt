package com.example.runh10.presentation

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.LaunchedEffect
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

/**
 * Hand-rolled screen states for the pre/post-run flow (this app predates
 * androidx.navigation — there's no NavController/back stack primitive here).
 * HOME is always the entry point (see MainActivity/WorkoutFlow — never PAIRING); Pairing
 * and Settings are both reachable from Home, and Settings also from Pairing's strap flow,
 * so [WorkoutFlow] tracks where Settings was opened from and returns there on back (both
 * the in-app BACK control and the system back gesture, via [BackHandler]).
 */
private enum class Screen { HOME, PAIRING, SETTINGS }

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
    // HOME is always the initial/resting screen — cold or warm launch never starts on
    // Pairing (V7), regardless of whether a strap is currently remembered.
    // Deliberately `remember`, not `rememberSaveable`: process recreation (e.g. an OS
    // memory-reclaim kill, not just cold launch) must always land on HOME (V7) rather
    // than restoring whatever screen — including SETTINGS/PAIRING — was showing before.
    var screen by remember { mutableStateOf(Screen.HOME) }
    // Where Settings was opened from, so BACK (in-app control AND system back gesture)
    // returns there instead of always bouncing to Home — this is the "real back stack"
    // for the one screen (Settings) that can be entered from more than one place.
    // Also deliberately `remember` — see `screen` above (V7).
    var settingsOrigin by remember { mutableStateOf(Screen.HOME) }
    var showSummary by remember { mutableStateOf(false) }
    var summaryUi by remember { mutableStateOf<UiState?>(null) }

    fun openSettings() {
        settingsOrigin = screen
        screen = Screen.SETTINGS
    }

    // Once a strap is actually remembered (WorkoutController persists it on reaching
    // CONNECTED — see MainActivity), leave Pairing automatically and land back on Home
    // with the newly-connected status, mirroring the pre-restructure behavior where the
    // Ready/Pair split was driven reactively off `remembered`.
    LaunchedEffect(remembered) {
        if (remembered != null && screen == Screen.PAIRING) {
            screen = Screen.HOME
        }
    }

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
        screen == Screen.SETTINGS -> {
            BackHandler(onBack = { screen = settingsOrigin })
            SettingsScreen(
                settings = settings,
                strapName = remembered?.name,
                strapConnected = ui.hrState == "CONNECTED",
                onForgetStrap = {
                    onForget()
                    screen = Screen.PAIRING
                },
                onAge = onAge,
                onMaxHr = onMaxHr,
                onMeasureResting = onMeasureResting,
                onToggleAnnounce = onToggleAnnounce,
                onToggleAnnounceSplit = onToggleAnnounceSplit,
                onToggleAnnouncePace = onToggleAnnouncePace,
                onToggleAnnounceZone = onToggleAnnounceZone,
                onToggleAutoPause = onToggleAutoPause,
                onBack = { screen = settingsOrigin },
            )
        }
        screen == Screen.PAIRING -> {
            // System back from Pairing always returns to Home — Pairing is never a
            // start destination and never loops into Settings and back (V7).
            BackHandler(onBack = { screen = Screen.HOME })
            PairingScreen(
                devices = devices,
                onScan = onScan,
                onPick = onPick,
                onSettings = { openSettings() },
            )
        }
        else -> ReadyScreen(
            ui = ui,
            remembered = remembered,
            onStart = onStart,
            onSettings = { openSettings() },
            onPairStrap = { screen = Screen.PAIRING },
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
