package com.example.runh10.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.example.runh10.media.WatchMediaClient
import com.example.runh10.presentation.theme.Heat
import kotlinx.coroutines.delay
import java.util.Locale

/** Swipe-left music control: now playing up top, transport + volume at the bottom. No clock. */
@Composable
fun MusicScreen(media: WatchMediaClient) {
    DisposableEffect(media) {
        media.start()
        onDispose { /* keep client running for the whole run */ }
    }

    val state by media.state.collectAsState()
    val local by media.localFallback.collectAsState()

    // 1 Hz tick so the progress bar advances between phone snapshots.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { nowMs = System.currentTimeMillis(); delay(1000) }
    }

    val s = state
    if (s == null || s.sourcePackage == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            MusicNoteTile(58.dp, dim = true)
            Spacer(Modifier.height(14.dp))
            Text(
                text = "NOTHING PLAYING",
                fontFamily = Heat.sairaCondensed,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.6.sp,
                color = Heat.textMuted,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Start music on your phone or watch",
                fontSize = 11.sp,
                color = Heat.textFaint,
            )
        }
        return
    }

    val position = if (s.playing) (s.positionMs + (nowMs - s.sentAtMs)).coerceAtMost(if (s.durationMs > 0) s.durationMs else Long.MAX_VALUE)
    else s.positionMs
    val frac = if (s.durationMs > 0) (position.toFloat() / s.durationMs).coerceIn(0f, 1f) else 0f

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 36.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Source row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(Heat.goodGreen, CircleShape))
                Spacer(Modifier.width(7.dp))
                Text(
                    text = (s.sourceApp ?: "MUSIC").uppercase(Locale.US) + if (local) " · WATCH" else "",
                    fontFamily = Heat.sairaCondensed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.7.sp,
                    color = Heat.textMuted,
                )
            }
            Spacer(Modifier.height(10.dp))
            MusicNoteTile(66.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                text = s.track ?: "—",
                fontFamily = Heat.sairaCondensed,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 19.sp,
                color = Heat.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 42.dp),
            )
            Text(
                text = s.artist ?: "",
                fontSize = 12.sp,
                color = Heat.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 46.dp, vertical = 2.dp),
            )

            // Progress
            Column(Modifier.padding(horizontal = 64.dp, vertical = 4.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Heat.border, RoundedCornerShape(2.dp)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(frac)
                            .height(4.dp)
                            .background(Heat.goodGreen, RoundedCornerShape(2.dp)),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmtTime(position), fontSize = 10.sp, color = Heat.textDim)
                    Text(if (s.durationMs > 0) fmtTime(s.durationMs) else "--:--", fontSize = 10.sp, color = Heat.textDim)
                }
            }

            Spacer(Modifier.height(4.dp))

            // Transport
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                SkipIcon(26.dp, forward = false, modifier = Modifier.clickable { media.prev() })
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(Heat.brandGradient, CircleShape)
                        .clickable { if (s.playing) media.pause() else media.play() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (s.playing) PauseIcon(22.dp) else PlayIcon(22.dp)
                }
                SkipIcon(26.dp, forward = true, modifier = Modifier.clickable { media.next() })
            }

            Spacer(Modifier.height(10.dp))

            // Volume slider (drag)
            var dragPct by remember(s.volumePct) { mutableStateOf(s.volumePct / 100f) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 58.dp),
            ) {
                VolIcon(14.dp, loud = false)
                Spacer(Modifier.width(9.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(16.dp)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = { media.setVolume((dragPct * 100).toInt()) },
                            ) { change, _ ->
                                dragPct = (change.position.x / size.width).coerceIn(0f, 1f)
                            }
                        },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(Modifier.fillMaxWidth().height(4.dp).background(Heat.border, RoundedCornerShape(2.dp)))
                    Box(Modifier.fillMaxWidth(dragPct).height(4.dp).background(Heat.textMuted, RoundedCornerShape(2.dp)))
                }
                Spacer(Modifier.width(9.dp))
                VolIcon(14.dp, loud = true)
            }
        }
    }
}

private fun fmtTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

@Composable
private fun MusicNoteTile(size: Dp, dim: Boolean = false) {
    Box(
        modifier = Modifier
            .size(size)
            .background(
                if (dim) Brush.linearGradient(listOf(Heat.surface, Heat.surfaceDeep))
                else Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFF2E9BE6))),
                RoundedCornerShape(16.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        NoteIcon(size * 0.42f, color = if (dim) Heat.textFaint else Color.White)
    }
}

@Composable
private fun NoteIcon(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        // beam + stems (simplified double note)
        val p = Path().apply {
            moveTo(w * 0.38f, h * 0.75f)
            lineTo(w * 0.38f, h * 0.2f)
            lineTo(w * 0.88f, h * 0.12f)
            lineTo(w * 0.88f, h * 0.66f)
        }
        drawPath(p, color, style = stroke)
        drawCircle(color, radius = w * 0.13f, center = Offset(w * 0.27f, h * 0.75f))
        drawCircle(color, radius = w * 0.13f, center = Offset(w * 0.77f, h * 0.66f))
    }
}

@Composable
private fun SkipIcon(size: Dp, forward: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val bar = w * 0.12f
        if (forward) {
            val p = Path().apply { moveTo(0f, h * 0.15f); lineTo(w * 0.62f, h * 0.5f); lineTo(0f, h * 0.85f); close() }
            drawPath(p, Heat.text)
            drawRoundRect(Heat.text, topLeft = Offset(w * 0.74f, h * 0.15f), size = androidx.compose.ui.geometry.Size(bar, h * 0.7f))
        } else {
            val p = Path().apply { moveTo(w, h * 0.15f); lineTo(w * 0.38f, h * 0.5f); lineTo(w, h * 0.85f); close() }
            drawPath(p, Heat.text)
            drawRoundRect(Heat.text, topLeft = Offset(w * 0.14f, h * 0.15f), size = androidx.compose.ui.geometry.Size(bar, h * 0.7f))
        }
    }
}

@Composable
private fun VolIcon(size: Dp, loud: Boolean) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        val p = Path().apply {
            moveTo(0f, h * 0.35f)
            lineTo(w * 0.28f, h * 0.35f)
            lineTo(w * 0.58f, h * 0.12f)
            lineTo(w * 0.58f, h * 0.88f)
            lineTo(w * 0.28f, h * 0.65f)
            lineTo(0f, h * 0.65f)
            close()
        }
        drawPath(p, Heat.textDim)
        if (loud) {
            drawArc(
                color = Heat.textDim,
                startAngle = -55f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(w * 0.42f, h * 0.2f),
                size = androidx.compose.ui.geometry.Size(w * 0.55f, h * 0.6f),
                style = Stroke(width = w * 0.1f, cap = StrokeCap.Round),
            )
        }
    }
}
