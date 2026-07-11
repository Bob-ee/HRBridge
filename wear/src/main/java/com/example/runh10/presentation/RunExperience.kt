@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.example.runh10.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.example.runh10.media.WatchMediaClient
import com.example.runh10.presentation.components.BezelZoneRing
import com.example.runh10.presentation.components.HeatChip
import com.example.runh10.presentation.components.PageDots
import com.example.runh10.presentation.components.PinnedClock
import com.example.runh10.presentation.components.ZoneChip
import com.example.runh10.presentation.components.zoneColor
import com.example.runh10.presentation.theme.Heat
import com.example.runh10.workout.RunState
import com.example.runh10.workout.UiState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The in-run swipe experience (HEAT redesign):
 * horizontal [ Controls | run data | Music ], vertical run data [ HR hero | Metrics | Laps ].
 * Time-of-day is pinned to the top of run-data screens only.
 */
@Composable
fun RunExperience(
    ui: UiState,
    ambientState: AmbientState,
    media: WatchMediaClient,
    onPauseToggle: () -> Unit,
    onStartNow: () -> Unit,
    onFinish: () -> Unit,
    onLap: () -> Unit,
) {
    if (ambientState.isAmbient) {
        AmbientActiveScreen(ui = ui, ambientState = ambientState)
        return
    }

    var locked by remember { mutableStateOf(false) }
    val hPager = rememberPagerState(initialPage = 1) { 3 }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(Heat.bg)) {
        HorizontalPager(state = hPager, userScrollEnabled = !locked) { page ->
            when (page) {
                0 -> ControlsScreen(
                    paused = ui.runState == RunState.MANUAL_PAUSED || ui.runState == RunState.AUTO_PAUSED,
                    warmup = ui.runState == RunState.WARMUP,
                    onPauseToggle = onPauseToggle,
                    onStartNow = onStartNow,
                    onFinish = onFinish,
                    onLap = {
                        onLap()
                        scope.launch { hPager.animateScrollToPage(1) }
                    },
                    onLock = {
                        locked = true
                        scope.launch { hPager.scrollToPage(1) }
                    },
                )
                1 -> RunDataPager(ui, locked, onStartNow)
                2 -> MusicScreen(media)
            }
        }

        if (locked) LockOverlay(onUnlock = { locked = false })
    }
}

@Composable
private fun RunDataPager(ui: UiState, locked: Boolean, onStartNow: () -> Unit) {
    val vPager = rememberPagerState { 3 }
    Box(Modifier.fillMaxSize()) {
        VerticalPager(state = vPager, userScrollEnabled = !locked) { page ->
            when (page) {
                0 -> HrHeroScreen(ui, onStartNow)
                1 -> MetricsScreen(ui)
                2 -> LapsScreen(ui)
            }
        }
        PinnedClock(Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
        PageDots(count = 3, active = vPager.currentPage, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
    }
}

// ─────────────────────────── HR hero ───────────────────────────

@Composable
private fun HrHeroScreen(ui: UiState, onStartNow: () -> Unit) {
    val paused = ui.runState == RunState.MANUAL_PAUSED || ui.runState == RunState.AUTO_PAUSED
    val warmup = ui.runState == RunState.WARMUP

    Box(Modifier.fillMaxSize()) {
        BezelZoneRing(
            zone = ui.hrZone,
            bpm = ui.bpm,
            zoneEdges = ui.zoneEdges,
            dim = warmup || paused,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = ui.bpm?.toString() ?: "––",
                fontFamily = Heat.sairaCondensed,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 92.sp,
                lineHeight = 80.sp,
                color = when {
                    ui.bpm == null -> Heat.textFaint
                    ui.hrStale -> zoneColor(ui.hrZone).copy(alpha = 0.4f)
                    else -> zoneColor(ui.hrZone)
                },
            )
            Text(
                text = "BPM",
                fontSize = 12.sp,
                letterSpacing = 1.7.sp,
                color = Heat.textMuted,
            )
            Spacer(Modifier.height(9.dp))
            when {
                warmup -> Box(Modifier.clickable(onClick = onStartNow)) {
                    HeatChip("WARMING UP · TAP TO START", Color(0xFFFFCF6A))
                }
                ui.runState == RunState.AUTO_PAUSED -> HeatChip("AUTO-PAUSED", Color(0xFFFFCF6A))
                ui.runState == RunState.MANUAL_PAUSED -> HeatChip("PAUSED", Heat.hrvPurple)
                else -> ZoneChip(ui.hrZone)
            }
        }

        // Bottom 3-up: TIME · MILES · /MILE
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeroStat(formatElapsed(ui.movingSec), "TIME")
            VDivider()
            HeroStat(formatMilesShort(ui.distanceMeters), "MILES")
            VDivider()
            HeroStat(if (warmup || ui.rollingPaceMps == null) "—" else formatPace(ui.rollingPaceMps), "/MILE")
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 13.dp),
    ) {
        Text(
            text = value,
            fontFamily = Heat.sairaCondensed,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
            color = Heat.text,
        )
        Text(text = label, fontSize = 9.sp, letterSpacing = 1.2.sp, color = Heat.textDim)
    }
}

@Composable
private fun VDivider() {
    Box(Modifier.size(width = 1.dp, height = 26.dp).background(Color(0xFF222A32)))
}

// ─────────────────────────── Metrics ───────────────────────────

@Composable
private fun MetricsScreen(ui: UiState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 66.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        BigMetric(formatMilesShort(ui.distanceMeters), "mi", "DISTANCE", Heat.text)
        HDivider()
        BigMetric(
            if (ui.rollingPaceMps == null) "—" else formatPace(ui.rollingPaceMps),
            "/mi", "CURRENT PACE", Heat.brandOrange,
        )
        HDivider()
        BigMetric(
            ui.cadenceSpm?.roundToInt()?.toString() ?: "—",
            "spm", "CADENCE", Heat.text,
        )
    }
}

@Composable
private fun BigMetric(value: String, unit: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontFamily = Heat.sairaCondensed,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 42.sp,
                lineHeight = 40.sp,
                color = color,
            )
            Text(
                text = unit,
                fontSize = 16.sp,
                color = Heat.textMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 5.dp),
            )
        }
        Text(text = label, fontSize = 11.sp, letterSpacing = 1.4.sp, color = Heat.textDim)
    }
}

@Composable
private fun HDivider() {
    Box(Modifier.size(width = 110.dp, height = 1.dp).background(Heat.hairline))
}

// ─────────────────────────── Laps ───────────────────────────

@Composable
private fun LapsScreen(ui: UiState) {
    val lap = ui.currentLap
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 52.dp, bottom = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = if (lap != null) "LAP ${lap.index} · IN PROGRESS" else "LAPS",
            fontFamily = Heat.sairaCondensed,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 1.9.sp,
            color = Heat.brandOrange,
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = lap?.paceMps?.let { formatPace(it) } ?: "—",
                fontFamily = Heat.sairaCondensed,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 60.sp,
                lineHeight = 56.sp,
                color = Heat.text,
            )
            Text(
                text = "PACE" + (lap?.avgBpm?.let { " · $it BPM" } ?: ""),
                fontSize = 11.sp,
                letterSpacing = 1.4.sp,
                color = Heat.textDim,
                modifier = Modifier.padding(top = 4.dp),
            )
            ui.lapDeltaSecPerMi?.let { delta ->
                Spacer(Modifier.height(10.dp))
                val faster = delta < 0
                val secs = kotlin.math.abs(delta).roundToInt()
                if (secs > 0) HeatChip(
                    "${secs}s ${if (faster) "faster" else "slower"} than avg",
                    if (faster) Heat.goodGreen else Heat.danger,
                )
            }
        }

        // Previous laps, latest first (up to 3).
        val prev = ui.splits.takeLast(3).reversed()
        if (prev.isEmpty()) {
            Text(text = "First lap under way", fontSize = 11.sp, color = Heat.textFaint)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                prev.forEach { s ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatPace(s.avgPaceMps),
                            fontFamily = Heat.sairaCondensed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Heat.textMuted,
                        )
                        Text(
                            text = "LAP ${s.index}",
                            fontSize = 9.sp,
                            letterSpacing = 1.2.sp,
                            color = Heat.textFaint,
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────── Controls ───────────────────────────

@Composable
private fun ControlsScreen(
    paused: Boolean,
    warmup: Boolean,
    onPauseToggle: () -> Unit,
    onStartNow: () -> Unit,
    onFinish: () -> Unit,
    onLap: () -> Unit,
    onLock: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 40.dp, bottom = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "RUN CONTROLS",
            fontFamily = Heat.sairaCondensed,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 14.sp,
            letterSpacing = 2.sp,
            color = Heat.textMuted,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            ControlCircle(
                size = 82.dp,
                gradient = true,
                label = if (warmup) "START" else if (paused) "RESUME" else "PAUSE",
                onClick = if (warmup) onStartNow else onPauseToggle,
            ) { if (paused || warmup) PlayIcon(26.dp) else PauseIcon(26.dp) }
            ControlCircle(
                size = 82.dp,
                danger = true,
                label = "FINISH",
                onClick = onFinish,
            ) { StopIcon(22.dp) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            ControlCircle(size = 64.dp, label = "LAP", onClick = onLap) { LapIcon(22.dp) }
            ControlCircle(size = 64.dp, label = "LOCK", onClick = onLock) { LockIcon(20.dp) }
        }
    }
}

@Composable
private fun ControlCircle(
    size: Dp,
    label: String,
    onClick: () -> Unit,
    gradient: Boolean = false,
    danger: Boolean = false,
    icon: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(size)
                .let {
                    when {
                        gradient -> it.background(Heat.brandGradient, CircleShape)
                        danger -> it
                            .background(Color(0xFF15110C), CircleShape)
                            .border(1.5.dp, Color(0xFF43242A), CircleShape)
                        else -> it
                            .background(Heat.surface, CircleShape)
                            .border(1.5.dp, Heat.borderStrong, CircleShape)
                    }
                }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            fontFamily = Heat.sairaCondensed,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.1.sp,
            color = if (danger) Heat.danger else if (gradient) Heat.text else Heat.textMuted,
        )
    }
}

// ─────────────────────────── Lock overlay ───────────────────────────

@Composable
private fun LockOverlay(onUnlock: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Heat.bg.copy(alpha = 0.55f))
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onUnlock() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(Heat.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) { LockIcon(20.dp) }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "HOLD TO UNLOCK",
                fontFamily = Heat.sairaCondensed,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.6.sp,
                color = Heat.textMuted,
            )
        }
    }
}

// ─────────────────────────── Ambient ───────────────────────────

/**
 * Low-power ambient layout: burn-in-safe near-black snapshot. Fast metrics show
 * "--"; monotonic time + distance stay visible.
 */
@Composable
fun AmbientActiveScreen(ui: UiState, ambientState: AmbientState) {
    val shift = if (ambientState.burnInProtection) 3.dp else 0.dp
    val timeColor = if (ambientState.lowBitAmbient) Color.White else Color(0xFFBFBFBF)
    val labelColor = if (ambientState.lowBitAmbient) Color.White else Color(0xFF666666)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Thin, solid, dim outline only — honors low-bit panels.
        Canvas(Modifier.fillMaxSize()) {
            drawArc(
                color = Color(0xFF3A3A3A),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                size = Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        Column(
            modifier = Modifier.fillMaxSize().offset(x = shift, y = shift),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = formatElapsed(ui.movingSec),
                fontFamily = Heat.sairaCondensed,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                color = timeColor,
            )
            Text(text = "MOVING TIME", fontSize = 9.sp, letterSpacing = 1.2.sp, color = labelColor)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatMilesShort(ui.distanceMeters), fontFamily = Heat.sairaCondensed, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = timeColor)
                    Text("MI", fontSize = 9.sp, color = labelColor)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("--", fontFamily = Heat.sairaCondensed, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = timeColor)
                    Text("BPM", fontSize = 9.sp, color = labelColor)
                }
            }
        }
    }
}

// ─────────────────────────── Icons (Canvas-drawn) ───────────────────────────

@Composable
fun PauseIcon(size: Dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val barW = w * 0.24f
        val r = CornerRadius(barW * 0.35f)
        drawRoundRect(color, topLeft = Offset(w * 0.16f, 0f), size = Size(barW, this.size.height), cornerRadius = r)
        drawRoundRect(color, topLeft = Offset(w * 0.60f, 0f), size = Size(barW, this.size.height), cornerRadius = r)
    }
}

@Composable
fun PlayIcon(size: Dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val p = Path().apply {
            moveTo(w * 0.22f, 0f)
            lineTo(w * 0.95f, h * 0.5f)
            lineTo(w * 0.22f, h)
            close()
        }
        drawPath(p, color)
    }
}

@Composable
fun StopIcon(size: Dp, color: Color = Heat.danger) {
    Canvas(Modifier.size(size)) {
        drawRoundRect(color, cornerRadius = CornerRadius(this.size.width * 0.18f))
    }
}

@Composable
fun LapIcon(size: Dp, color: Color = Heat.text) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val stroke = Stroke(width = w * 0.1f, cap = StrokeCap.Round)
        drawCircle(color, radius = w * 0.44f, style = stroke)
        // hands: 12 → center → ~4 o'clock
        drawLine(color, Offset(w / 2f, w * 0.24f), Offset(w / 2f, w / 2f), strokeWidth = w * 0.1f, cap = StrokeCap.Round)
        drawLine(color, Offset(w / 2f, w / 2f), Offset(w * 0.68f, w * 0.62f), strokeWidth = w * 0.1f, cap = StrokeCap.Round)
    }
}

@Composable
fun LockIcon(size: Dp, color: Color = Heat.text) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val stroke = Stroke(width = w * 0.11f, cap = StrokeCap.Round)
        // body
        drawRoundRect(
            color,
            topLeft = Offset(w * 0.14f, h * 0.45f),
            size = Size(w * 0.72f, h * 0.48f),
            cornerRadius = CornerRadius(w * 0.12f),
            style = stroke,
        )
        // shackle
        val p = Path().apply {
            moveTo(w * 0.30f, h * 0.45f)
            lineTo(w * 0.30f, h * 0.30f)
            cubicTo(w * 0.30f, h * 0.08f, w * 0.70f, h * 0.08f, w * 0.70f, h * 0.30f)
            lineTo(w * 0.70f, h * 0.45f)
        }
        drawPath(p, color, style = stroke)
    }
}
