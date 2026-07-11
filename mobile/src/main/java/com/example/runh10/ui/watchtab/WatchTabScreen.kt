package com.example.runh10.ui.watchtab

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.runh10.ui.SyncViewModel
import com.example.runh10.ui.components.GradientButton
import com.example.runh10.ui.components.HeatCard
import com.example.runh10.ui.components.SectionLabel
import com.example.runh10.ui.theme.Heat

/** Watch tab: the Phase-3 sync console, restyled in HEAT. */
@Composable
fun WatchTabScreen(
    vm: SyncViewModel,
    onRequestPermissions: () -> Unit,
    bottomPadding: PaddingValues,
) {
    val state by vm.state.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(Heat.bg)
            .verticalScroll(rememberScrollState())
            .padding(bottomPadding)
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Text("Watch", fontFamily = Heat.sairaCondensed, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = Heat.text)
        Spacer(Modifier.height(16.dp))

        HeatCard(Modifier.fillMaxWidth(), padding = 18.dp) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(10.dp).background(
                            if (state.hcAvailable && state.permissionsGranted) Heat.goodGreen else Heat.danger,
                            CircleShape,
                        ),
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        when {
                            !state.hcAvailable -> "Health Connect unavailable"
                            !state.permissionsGranted -> "Health Connect permissions needed"
                            state.syncing -> "Syncing runs from watch…"
                            else -> "Ready · syncs automatically"
                        },
                        fontFamily = Heat.sairaCondensed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Heat.text,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Runs recorded on the watch land in your feed and Health Connect when the phone is nearby.",
                    fontSize = 12.sp,
                    color = Heat.textDim,
                )
                Spacer(Modifier.height(16.dp))
                if (!state.permissionsGranted && state.hcAvailable) {
                    GradientButton("GRANT HEALTH CONNECT", onRequestPermissions, Modifier.fillMaxWidth())
                } else {
                    GradientButton(
                        if (state.syncing) "SYNCING…" else "SYNC NOW",
                        onClick = { vm.syncNow() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.syncing,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (state.log.isNotEmpty()) {
            // Every resume/auto-sync appends a fresh progress line, even when nothing
            // changed (e.g. repeated "No unsynced runs"), so the raw log accumulates
            // runs of identical consecutive entries. Collapse those to one line each —
            // matching the app's other single-line empty states (e.g. Trends' "Not
            // enough runs with HRV yet") — while leaving genuinely distinct entries
            // (different runs synced) rendered as-is.
            val displayLog = remember(state.log) {
                state.log.takeLast(14).fold(mutableListOf<String>()) { acc, line ->
                    if (acc.lastOrNull() != line) acc.add(line)
                    acc
                }
            }
            SectionLabel("SYNC LOG")
            Spacer(Modifier.height(8.dp))
            HeatCard(Modifier.fillMaxWidth(), padding = 14.dp, background = Heat.surfaceDeep) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    displayLog.forEach { line ->
                        Text(
                            line,
                            fontSize = 12.sp,
                            color = when {
                                line.startsWith("✓") -> Heat.goodGreen
                                line.startsWith("✗") -> Heat.danger
                                else -> Heat.textMuted
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}
