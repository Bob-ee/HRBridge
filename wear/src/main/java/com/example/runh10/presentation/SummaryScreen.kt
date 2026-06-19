package com.example.runh10.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.runh10.shared.model.Split
import com.example.runh10.workout.UiState
import java.util.Locale

@Composable
fun SummaryScreen(ui: UiState, onDone: () -> Unit) {
    val totalElevFt = ui.splits.sumOf { it.elevationGainM } * 3.28084
    val elevDisplay = if (ui.splits.isEmpty()) "—"
    else String.format(Locale.US, "%.0f ft", totalElevFt)

    val avgHrDisplay = weightedAvgHr(ui.splits)

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Summary",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
        }

        // Totals section
        item { SummaryRow(label = "Distance", value = formatMiles(ui.distanceMeters)) }
        item { SummaryRow(label = "Time", value = formatElapsed(ui.elapsedSec)) }
        item {
            SummaryRow(
                label = "Avg Pace",
                value = if (ui.avgPaceMps != null) "${formatPace(ui.avgPaceMps)} /mi" else "—",
            )
        }
        item { SummaryRow(label = "Avg HR", value = avgHrDisplay) }
        item { SummaryRow(label = "Calories", value = "—") }
        item { SummaryRow(label = "Elevation", value = elevDisplay) }

        // Splits table header
        if (ui.splits.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Mi",
                        fontSize = 10.sp,
                        color = Color(0xFF888888),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "Pace",
                        fontSize = 10.sp,
                        color = Color(0xFF888888),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(2f),
                    )
                    Text(
                        text = "Time",
                        fontSize = 10.sp,
                        color = Color(0xFF888888),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(2f),
                    )
                    Text(
                        text = "HR",
                        fontSize = 10.sp,
                        color = Color(0xFF888888),
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            items(ui.splits) { split ->
                SplitRow(split)
            }
        }

        // Done chip
        item {
            Spacer(Modifier.height(8.dp))
            Chip(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                onClick = onDone,
                colors = ChipDefaults.primaryChipColors(),
                label = {
                    Text(
                        text = "Done",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color(0xFF888888),
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
        )
    }
}

@Composable
private fun SplitRow(split: Split) {
    val splitTimeSec = split.movingDurationMs / 1000L
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${split.index}",
            fontSize = 12.sp,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${formatPace(split.avgPaceMps)} /mi",
            fontSize = 12.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(2f),
        )
        Text(
            text = formatElapsed(splitTimeSec),
            fontSize = 12.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(2f),
        )
        Text(
            text = split.avgBpm?.toString() ?: "—",
            fontSize = 12.sp,
            color = Color.White,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Weighted average HR across splits, using movingDurationMs as the weight.
 * Splits with null avgBpm are excluded. Returns "—" if no splits have HR data.
 */
internal fun weightedAvgHr(splits: List<Split>): String {
    val validSplits = splits.filter { it.avgBpm != null }
    if (validSplits.isEmpty()) return "—"
    val totalWeight = validSplits.sumOf { it.movingDurationMs.toDouble() }
    if (totalWeight <= 0.0) return "—"
    val weightedSum = validSplits.sumOf { it.avgBpm!! * it.movingDurationMs.toDouble() }
    return "${(weightedSum / totalWeight).toInt()} bpm"
}
