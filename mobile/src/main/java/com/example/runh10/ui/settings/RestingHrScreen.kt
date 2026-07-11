package com.example.runh10.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.runh10.data.AthleteProfile
import com.example.runh10.data.AthleteStore
import com.example.runh10.record.PhoneRecordController
import com.example.runh10.ui.components.GradientButton
import com.example.runh10.ui.theme.Heat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class MeasurePhase { IDLE, MEASURING, DONE }

/**
 * Interactive resting-HR measurement: reads the lowest stable rate from the live H10
 * stream over the configured duration; SAVE writes it back and zones recompute.
 */
@Composable
fun RestingHrScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { PhoneRecordController.init(context.applicationContext) }
    val store = remember { AthleteStore(context) }
    val profile = store.profile.collectAsState(initial = AthleteProfile()).value
    val scope = rememberCoroutineScope()

    val recordUi by PhoneRecordController.ui.collectAsState()
    val connected = recordUi.bleState == "CONNECTED"
    val liveBpm = recordUi.bpm

    var phase by remember { mutableStateOf(MeasurePhase.IDLE) }
    var elapsed by remember { mutableIntStateOf(0) }
    var readings by remember { mutableStateOf(listOf<Int>()) }
    var result by remember { mutableIntStateOf(0) }
    val duration = profile.measureDurationSec

    // Sampling loop while measuring: 1 Hz snapshot of the live BPM.
    // Read straight from the controller's flow each tick — the composable-scoped
    // `liveBpm` would be a stale capture inside this long-running effect.
    LaunchedEffect(phase) {
        if (phase != MeasurePhase.MEASURING) return@LaunchedEffect
        elapsed = 0
        readings = emptyList()
        while (elapsed < duration) {
            delay(1000)
            elapsed += 1
            PhoneRecordController.ui.value.bpm?.let { readings = readings + it }
        }
        // Lowest stable rate: mean of the lowest 20% of samples.
        result = readings.sorted().take((readings.size / 5).coerceAtLeast(1)).takeIf { it.isNotEmpty() }
            ?.average()?.toInt() ?: 0
        phase = if (result > 0) MeasurePhase.DONE else MeasurePhase.IDLE
    }

    Column(
        Modifier.fillMaxSize().background(Heat.bg).statusBarsPadding().padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.align(Alignment.Start), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹", fontSize = 26.sp, color = Color.White, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp),
            )
            Text("Resting HR", fontFamily = Heat.sairaCondensed, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = Heat.text)
        }
        Spacer(Modifier.height(24.dp))

        when (phase) {
            MeasurePhase.IDLE -> {
                Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(
                            Heat.borderStrong,
                            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))),
                        )
                        drawCircle(
                            Heat.infoBlue.copy(alpha = 0.07f),
                            radius = size.minDimension / 2f - 26.dp.toPx(),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("♥", fontSize = 42.sp, color = Heat.infoBlue)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "LAST · ${profile.restingHr ?: "—"} BPM",
                            fontFamily = Heat.sairaCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 1.4.sp,
                            color = Heat.textMuted,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    "Measure your resting HR",
                    fontFamily = Heat.sairaCondensed,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    color = Heat.text,
                )
                Text(
                    "Sit still and breathe normally with the H10 on. We'll read your lowest stable rate over ${duration}s.",
                    fontSize = 13.sp,
                    color = Heat.textDim,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp),
                )
                Spacer(Modifier.height(26.dp))
                GradientButton(
                    "START · ${duration}s",
                    onClick = { if (connected) phase = MeasurePhase.MEASURING },
                    modifier = Modifier.width(200.dp),
                    enabled = connected,
                )
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(7.dp).background(
                            if (connected) Heat.goodGreen else Color(0xFFFFCF6A), CircleShape,
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (connected) "Polar H10 connected · signal strong" else "Connect your H10 on the record screen first",
                        fontSize = 12.sp,
                        color = if (connected) Heat.goodGreen else Heat.textDim,
                    )
                }
            }

            MeasurePhase.MEASURING -> {
                val progress = elapsed.toFloat() / duration
                Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 14.dp.toPx()
                        val insetPx = stroke / 2f
                        drawArc(
                            Heat.border,
                            startAngle = -90f, sweepAngle = 360f, useCenter = false,
                            topLeft = Offset(insetPx, insetPx),
                            size = Size(size.width - stroke, size.height - stroke),
                            style = Stroke(stroke),
                        )
                        drawArc(
                            Color(0xFFF59E0B),
                            startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                            topLeft = Offset(insetPx, insetPx),
                            size = Size(size.width - stroke, size.height - stroke),
                            style = Stroke(stroke),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            liveBpm?.toString() ?: "—",
                            fontFamily = Heat.sairaCondensed,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 74.sp,
                            lineHeight = 66.sp,
                            color = Heat.infoBlue,
                        )
                        Text("BPM · LIVE", fontSize = 12.sp, letterSpacing = 1.4.sp, color = Heat.textMuted)
                        Spacer(Modifier.height(10.dp))
                        val rem = duration - elapsed
                        Text(
                            "%d:%02d left".format(rem / 60, rem % 60),
                            fontFamily = Heat.sairaCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFFF59E0B),
                        )
                    }
                }
                Spacer(Modifier.height(26.dp))
                Text("Keep still…", fontFamily = Heat.sairaCondensed, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Heat.text)
                Text("Reading your heartbeat from the H10 strap", fontSize = 13.sp, color = Heat.textDim, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(30.dp))
                Box(
                    Modifier
                        .width(178.dp)
                        .height(50.dp)
                        .background(Heat.surface, RoundedCornerShape(16.dp))
                        .border(1.dp, Heat.borderStrong, RoundedCornerShape(16.dp))
                        .clickable { phase = MeasurePhase.IDLE },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("CANCEL", fontFamily = Heat.sairaCondensed, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 0.8.sp, color = Heat.textMuted)
                }
            }

            MeasurePhase.DONE -> {
                Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(Heat.goodGreen, style = Stroke(14.dp.toPx()))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$result",
                            fontFamily = Heat.sairaCondensed,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 80.sp,
                            lineHeight = 70.sp,
                            color = Color.White,
                        )
                        Text("BPM RESTING", fontSize = 12.sp, letterSpacing = 1.4.sp, color = Heat.textMuted)
                    }
                }
                Spacer(Modifier.height(22.dp))
                Text("✓ Stable reading captured", fontFamily = Heat.sairaCondensed, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Heat.goodGreen)
                Text("Zones will update from your new resting HR", fontSize = 13.sp, color = Heat.textDim, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .width(120.dp).height(52.dp)
                            .background(Heat.surface, RoundedCornerShape(16.dp))
                            .border(1.dp, Heat.borderStrong, RoundedCornerShape(16.dp))
                            .clickable { phase = MeasurePhase.MEASURING },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("RETAKE", fontFamily = Heat.sairaCondensed, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Heat.textMuted)
                    }
                    Box(
                        Modifier
                            .width(120.dp).height(52.dp)
                            .background(Heat.brandGradient, RoundedCornerShape(16.dp))
                            .clickable {
                                scope.launch {
                                    store.setRestingHr(result)
                                    onBack()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("SAVE", fontFamily = Heat.sairaCondensed, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.White)
                    }
                }
            }
        }
    }
}
