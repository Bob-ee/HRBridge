package com.example.runh10.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import com.example.runh10.data.RunSettings
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: RunSettings,
    onAge: (Int) -> Unit,
    onMaxHr: (Int) -> Unit,
    onMeasureResting: suspend () -> Int,
    onToggleAnnounce: (Boolean) -> Unit,
    onToggleAnnounceSplit: (Boolean) -> Unit,
    onToggleAnnouncePace: (Boolean) -> Unit,
    onToggleAnnounceZone: (Boolean) -> Unit,
    onToggleAutoPause: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var measuring by remember { mutableStateOf(false) }
    var measuredHr by remember { mutableStateOf<Int?>(null) }
    var noStrapData by remember { mutableStateOf(false) }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Settings",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colors.primary,
            )
        }

        // Age stepper
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Age: ${settings.age ?: "—"}",
                fontSize = 13.sp,
                color = MaterialTheme.colors.onBackground,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { onAge(maxOf(1, (settings.age ?: 30) - 1)) },
                    modifier = Modifier.size(36.dp),
                    colors = ButtonDefaults.secondaryButtonColors(),
                ) { Text("−") }
                Button(
                    onClick = { onAge((settings.age ?: 30) + 1) },
                    modifier = Modifier.size(36.dp),
                    colors = ButtonDefaults.secondaryButtonColors(),
                ) { Text("+") }
            }
        }

        // Max HR stepper
        item {
            Spacer(Modifier.height(8.dp))
            val ageEstimate = settings.age?.let { 220 - it }
            val maxHrDisplay = when {
                settings.maxHr != null -> "${settings.maxHr} bpm"
                ageEstimate != null -> "~$ageEstimate (est.)"
                else -> "—"
            }
            Text(
                text = "Max HR: $maxHrDisplay",
                fontSize = 13.sp,
                color = MaterialTheme.colors.onBackground,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        val current = settings.maxHr ?: (settings.age?.let { 220 - it } ?: 180)
                        onMaxHr(maxOf(100, current - 1))
                    },
                    modifier = Modifier.size(36.dp),
                    colors = ButtonDefaults.secondaryButtonColors(),
                ) { Text("−") }
                Button(
                    onClick = {
                        val current = settings.maxHr ?: (settings.age?.let { 220 - it } ?: 180)
                        onMaxHr(current + 1)
                    },
                    modifier = Modifier.size(36.dp),
                    colors = ButtonDefaults.secondaryButtonColors(),
                ) { Text("+") }
            }
        }

        // Measure resting HR
        item {
            Spacer(Modifier.height(8.dp))
            val chipLabel = when {
                measuring -> "Measuring… (60s)"
                noStrapData -> "No strap data"
                measuredHr != null -> "Rest HR: $measuredHr bpm"
                settings.restingHr != null -> "Rest HR: ${settings.restingHr} bpm"
                else -> "Measure resting HR"
            }
            Chip(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (!measuring) {
                        measuring = true
                        scope.launch {
                            val result = onMeasureResting()
                            if (result > 0) {
                                measuredHr = result
                                noStrapData = false
                            } else {
                                noStrapData = true
                            }
                            measuring = false
                        }
                    }
                },
                colors = ChipDefaults.primaryChipColors(),
                label = {
                    Text(
                        text = chipLabel,
                        textAlign = TextAlign.Center,
                    )
                },
            )
        }

        // Announcements master toggle
        item {
            Spacer(Modifier.height(8.dp))
            ToggleChip(
                modifier = Modifier.fillMaxWidth(),
                checked = settings.announce,
                onCheckedChange = onToggleAnnounce,
                label = { Text("Announcements") },
                toggleControl = { Switch(checked = settings.announce) },
                colors = ToggleChipDefaults.toggleChipColors(),
            )
        }

        item {
            ToggleChip(
                modifier = Modifier.fillMaxWidth(),
                checked = settings.announceSplitTime,
                onCheckedChange = onToggleAnnounceSplit,
                label = { Text("  Split time") },
                toggleControl = { Switch(checked = settings.announceSplitTime) },
                colors = ToggleChipDefaults.toggleChipColors(),
            )
        }
        item {
            ToggleChip(
                modifier = Modifier.fillMaxWidth(),
                checked = settings.announcePace,
                onCheckedChange = onToggleAnnouncePace,
                label = { Text("  Pace") },
                toggleControl = { Switch(checked = settings.announcePace) },
                colors = ToggleChipDefaults.toggleChipColors(),
            )
        }
        item {
            ToggleChip(
                modifier = Modifier.fillMaxWidth(),
                checked = settings.announceHrZone,
                onCheckedChange = onToggleAnnounceZone,
                label = { Text("  HR Zone") },
                toggleControl = { Switch(checked = settings.announceHrZone) },
                colors = ToggleChipDefaults.toggleChipColors(),
            )
        }

        // Auto-pause toggle
        item {
            Spacer(Modifier.height(4.dp))
            ToggleChip(
                modifier = Modifier.fillMaxWidth(),
                checked = settings.autoPause,
                onCheckedChange = onToggleAutoPause,
                label = { Text("Auto-pause") },
                toggleControl = { Switch(checked = settings.autoPause) },
                colors = ToggleChipDefaults.toggleChipColors(),
            )
        }

        // Back chip
        item {
            Spacer(Modifier.height(8.dp))
            Chip(
                modifier = Modifier.fillMaxWidth(),
                onClick = onBack,
                colors = ChipDefaults.secondaryChipColors(),
                label = { Text("Back") },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
