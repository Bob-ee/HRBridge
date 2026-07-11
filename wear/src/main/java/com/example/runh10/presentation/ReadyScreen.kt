package com.example.runh10.presentation

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.example.runh10.presentation.components.PulseLogo
import com.example.runh10.presentation.theme.Heat
import com.example.runh10.workout.ScanDevice
import com.example.runh10.workout.UiState

/**
 * Home / pre-run screen (HEAT): logo, big gradient START, strap + GPS status, visible
 * SETTINGS affordance. This is ALWAYS the landing screen — regardless of whether a strap
 * is remembered — so cold/warm launch never traps the user on Pairing (see V7). When no
 * strap is configured, the status line reads "TAP TO PAIR STRAP" and opens Pairing (V2).
 *
 * Sizing note (V1 root cause): this is a 456x456px / 320dpi round display, i.e. only
 * ~228dp of actual diameter — NOT ~456dp. The previous layout's children summed to
 * ~300dp and were positioned with `Arrangement.SpaceBetween` across the full
 * `fillMaxSize()`, which on a 228dp viewport made the bottom status column overlap and
 * render underneath the START circle (the "invisible" SETTINGS hotspot). This version's
 * interactive stack (circle + status + SETTINGS) is sized to fit comfortably inside the
 * ~78%-diameter round-safe band (~178dp) with margin to spare, centered rather than
 * pinned to the container edges.
 */
@Composable
fun ReadyScreen(
    ui: UiState,
    remembered: ScanDevice?,
    onStart: () -> Unit,
    onSettings: () -> Unit,
    onPairStrap: () -> Unit,
) {
    val connected = ui.hrState == "CONNECTED"
    val hasStrap = remembered != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Heat.bg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulseLogo(14.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = "HR BRIDGE",
                fontFamily = Heat.sairaCondensed,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                letterSpacing = 1.sp,
                color = Heat.text,
            )
        }

        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .size(96.dp)
                .alpha(if (connected) 1f else 0.45f)
                .background(Heat.brandGradient, CircleShape)
                .clickable(enabled = connected, onClick = onStart),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "START",
                    fontFamily = Heat.sairaCondensed,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 21.sp,
                    letterSpacing = 1.2.sp,
                    color = Heat.text,
                )
                Text(
                    text = "RUN",
                    fontFamily = Heat.sairaCondensed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 2.8.sp,
                    color = Heat.text.copy(alpha = if (connected) 0.8f else 0.5f),
                )
            }
        }

        Spacer(Modifier.height(9.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (hasStrap) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(6.dp).background(
                            if (connected) Heat.goodGreen else Color(0xFFFFCF6A),
                            CircleShape,
                        ),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = (remembered?.name?.uppercase() ?: "POLAR H10") + " · " +
                            if (connected) "CONNECTED" else "CONNECTING…",
                        fontFamily = Heat.sairaCondensed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.4.sp,
                        color = Heat.text,
                    )
                }
                Spacer(Modifier.height(3.dp))
                // ReadyScreen only ever shows pre-run, and Health Services GPS doesn't
                // engage until the run actually starts — so a "searching"/"locked"
                // status here would describe activity that isn't happening yet (and
                // "locked" could never truthfully appear). Honest static copy instead.
                // Live pre-run GPS sampling would need its own location stream — out of
                // scope here; candidate for a later polish plan.
                Text(
                    text = "GPS · ON AT START",
                    fontSize = 10.sp,
                    color = Heat.textMuted,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable(onClick = onPairStrap)
                        .padding(4.dp),
                ) {
                    Box(
                        Modifier.size(6.dp).background(Color(0xFFFFCF6A), CircleShape),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "TAP TO PAIR STRAP",
                        fontFamily = Heat.sairaCondensed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.4.sp,
                        color = Heat.brandOrange,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .background(Heat.surface, RoundedCornerShape(11.dp))
                    .border(1.dp, Heat.border, RoundedCornerShape(11.dp))
                    .clickable(onClick = onSettings)
                    .padding(horizontal = 14.dp, vertical = 5.dp),
            ) {
                Text(
                    text = "SETTINGS",
                    fontFamily = Heat.sairaCondensed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.4.sp,
                    color = Heat.textMuted,
                )
            }
        }
    }
}

/**
 * Explicit Pairing screen. Reached from Home's "TAP TO PAIR STRAP" status line (when no
 * strap is configured) and from Settings' strap row — never rendered as a start
 * destination (see V7/MainActivity + WorkoutFlow for the navigation state that enforces
 * this).
 */
@Composable
fun PairingScreen(
    devices: List<ScanDevice>,
    onScan: () -> Unit,
    onPick: (String) -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Heat.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PulseLogo(16.dp)
            Spacer(Modifier.width(7.dp))
            Text(
                text = "PAIR YOUR STRAP",
                fontFamily = Heat.sairaCondensed,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                letterSpacing = 1.2.sp,
                color = Heat.text,
            )
        }
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Heat.brandGradient, RoundedCornerShape(16.dp))
                .clickable(onClick = onScan)
                .padding(vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "SCAN",
                fontFamily = Heat.sairaCondensed,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                letterSpacing = 1.5.sp,
                color = Heat.text,
            )
        }
        Spacer(Modifier.height(10.dp))
        if (devices.isEmpty()) {
            Text(
                text = "No straps yet — tap Scan and wake the H10 (moisten the pads).",
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                color = Heat.textMuted,
            )
        } else {
            devices.forEach { d ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .background(Heat.surface, RoundedCornerShape(14.dp))
                        .border(1.dp, Heat.border, RoundedCornerShape(14.dp))
                        .clickable { onPick(d.address) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PulseLogo(14.dp)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = d.name,
                            fontFamily = Heat.sairaCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Heat.text,
                        )
                        Text(text = "${d.rssi} dBm", fontSize = 10.sp, color = Heat.textDim)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "SETTINGS",
            fontFamily = Heat.sairaCondensed,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.6.sp,
            color = Heat.textDim,
            modifier = Modifier
                .clickable(onClick = onSettings)
                .padding(6.dp),
        )
    }
}
