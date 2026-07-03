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
 * Pre-run screen (HEAT): logo, big gradient START, strap + GPS status.
 * Falls back to the scan/pick list when no strap is remembered.
 */
@Composable
fun ReadyScreen(
    ui: UiState,
    devices: List<ScanDevice>,
    remembered: ScanDevice?,
    onScan: () -> Unit,
    onPick: (String) -> Unit,
    onStart: () -> Unit,
    onSettings: () -> Unit,
) {
    if (remembered == null) {
        PairScreen(devices, onScan, onPick, onSettings)
        return
    }

    val connected = ui.hrState == "CONNECTED"

    Column(
        modifier = Modifier.fillMaxSize().background(Heat.bg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 38.dp),
        ) {
            PulseLogo(18.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "HR BRIDGE",
                fontFamily = Heat.sairaCondensed,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp,
                letterSpacing = 1.sp,
                color = Heat.text,
            )
        }

        Box(
            modifier = Modifier
                .size(142.dp)
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
                    fontSize = 32.sp,
                    letterSpacing = 1.8.sp,
                    color = Heat.text,
                )
                Text(
                    text = "RUN",
                    fontFamily = Heat.sairaCondensed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 4.sp,
                    color = Heat.text.copy(alpha = 0.8f),
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 34.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(7.dp).background(
                        if (connected) Heat.goodGreen else Color(0xFFFFCF6A),
                        CircleShape,
                    ),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = if (connected) remembered.name.uppercase() else "CONNECTING…",
                    fontFamily = Heat.sairaCondensed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 0.7.sp,
                    color = Heat.text,
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = "GPS starts with the run",
                fontSize = 11.sp,
                color = Heat.textMuted,
            )
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
}

@Composable
private fun PairScreen(
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
