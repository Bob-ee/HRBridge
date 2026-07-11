package com.example.runh10.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Text
import com.example.runh10.data.RunSettings
import com.example.runh10.presentation.components.PulseLogo
import com.example.runh10.presentation.theme.Heat
import kotlinx.coroutines.launch

/** Watch settings in HEAT: sensor/GPS/units rows, heart profile, audio coaching toggles. */
@Composable
fun SettingsScreen(
    settings: RunSettings,
    strapName: String?,
    strapConnected: Boolean,
    onForgetStrap: () -> Unit,
    onAge: (Int) -> Unit,
    onMaxHr: (Int) -> Unit,
    onMeasureResting: suspend (onTick: (elapsedSec: Int) -> Unit) -> Int,
    onToggleAnnounce: (Boolean) -> Unit,
    onToggleAnnounceSplit: (Boolean) -> Unit,
    onToggleAnnouncePace: (Boolean) -> Unit,
    onToggleAnnounceZone: (Boolean) -> Unit,
    onToggleAutoPause: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var measuring by remember { mutableStateOf(false) }
    var noStrapData by remember { mutableStateOf(false) }
    // V6: elapsed seconds of the current measurement, ticked live by the per-second
    // loop in WorkoutController.measureRestingHr — drives the countdown label.
    var elapsedSec by remember { mutableStateOf(0) }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxWidth().background(Heat.bg).padding(horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Settings",
                fontFamily = Heat.sairaCondensed,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 19.sp,
                color = Heat.text,
            )
            Spacer(Modifier.height(6.dp))
        }

        // ── Sensor / GPS / Units rows (mock) ──
        item {
            SettingRow(
                leading = { PulseLogo(16.dp) },
                title = strapName ?: "Polar H10",
                trailing = {
                    Box(
                        Modifier.size(8.dp).background(
                            if (strapConnected) Heat.goodGreen else Heat.textFaint,
                            CircleShape,
                        ),
                    )
                },
                onClick = onForgetStrap,
                subtitle = "tap to change strap",
            )
        }
        item {
            SettingRow(
                leading = { GpsPin() },
                title = "GPS",
                trailing = { ValueText("High") },
            )
        }
        item {
            SettingRow(
                leading = { BarsIcon() },
                title = "Units",
                trailing = { ValueText("Miles", Heat.brandOrange) },
            )
        }

        // ── Heart profile ──
        item { SectionLabel("HEART RATE") }
        item {
            StepperRow(
                title = "Age",
                value = settings.age?.toString() ?: "—",
                onMinus = { onAge(maxOf(1, (settings.age ?: 30) - 1)) },
                onPlus = { onAge((settings.age ?: 30) + 1) },
            )
        }
        item {
            val ageEstimate = settings.age?.let { (208 - 0.7 * it).toInt() }
            StepperRow(
                title = "Max HR",
                value = settings.maxHr?.toString() ?: (ageEstimate?.let { "~$it" } ?: "—"),
                onMinus = {
                    val current = settings.maxHr ?: ageEstimate ?: 180
                    onMaxHr(maxOf(100, current - 1))
                },
                onPlus = {
                    val current = settings.maxHr ?: ageEstimate ?: 180
                    onMaxHr(current + 1)
                },
            )
        }
        item {
            val label = when {
                // Live countdown (V6) — ticks each second off the elapsed counter driven
                // by the measurement's own per-second loop, instead of a frozen "(60s)".
                measuring -> "Measuring… ${(60 - elapsedSec).coerceIn(0, 60)}s"
                noStrapData -> "No strap data — wear the H10"
                settings.restingHr != null -> "Resting HR · ${settings.restingHr} bpm"
                else -> "Measure resting HR"
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .background(
                        if (measuring) Heat.surface else Heat.surfaceDeep,
                        RoundedCornerShape(14.dp),
                    )
                    .border(1.dp, Color(0xFF1E2A36), RoundedCornerShape(14.dp))
                    // Also the RETRY affordance for the terminal no-data state (V6):
                    // tapping here is enabled any time we're not already mid-measurement,
                    // so it restarts the measurement in place.
                    .clickable(enabled = !measuring) {
                        measuring = true
                        elapsedSec = 0
                        scope.launch {
                            val result = onMeasureResting { sec -> elapsedSec = sec }
                            noStrapData = result <= 0
                            measuring = false
                        }
                    }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = label,
                        fontFamily = Heat.sairaCondensed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Heat.infoBlue,
                    )
                    // Same subtitle idiom as the strap row's "tap to change strap".
                    if (noStrapData) {
                        Text(text = "tap to retry", fontSize = 10.sp, color = Heat.textDim)
                    }
                }
            }
        }

        // ── Audio coaching ──
        item { SectionLabel("AUDIO COACHING") }
        item { ToggleRow("Voice coach", settings.announce, onToggleAnnounce) }
        item { ToggleRow("Split time", settings.announceSplitTime, onToggleAnnounceSplit, indent = true) }
        item { ToggleRow("Pace", settings.announcePace, onToggleAnnouncePace, indent = true) }
        item { ToggleRow("HR zone", settings.announceHrZone, onToggleAnnounceZone, indent = true) }
        item { SectionLabel("RUN") }
        item { ToggleRow("Auto-pause", settings.autoPause, onToggleAutoPause) }

        item {
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .background(Heat.surface, RoundedCornerShape(15.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 26.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "BACK",
                    fontFamily = Heat.sairaCondensed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.5.sp,
                    color = Heat.textMuted,
                )
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = Heat.sairaCondensed,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 1.8.sp,
        color = Heat.textDim,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(
    leading: @Composable () -> Unit,
    title: String,
    trailing: @Composable () -> Unit,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(Heat.surface, RoundedCornerShape(16.dp))
            .border(1.dp, Heat.border, RoundedCornerShape(16.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = Heat.sairaCondensed,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Heat.text,
            )
            if (subtitle != null) Text(text = subtitle, fontSize = 10.sp, color = Heat.textDim)
        }
        trailing()
    }
}

@Composable
private fun ValueText(text: String, color: Color = Heat.textMuted) {
    Text(
        text = text,
        fontFamily = Heat.sairaCondensed,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        color = color,
    )
}

@Composable
private fun StepperRow(title: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(Heat.surface, RoundedCornerShape(16.dp))
            .border(1.dp, Heat.border, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = Heat.sairaCondensed,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Heat.text,
            )
            Text(
                text = value,
                fontFamily = Heat.sairaCondensed,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp,
                color = Heat.brandOrange,
            )
        }
        StepBtn("−", onMinus)
        Spacer(Modifier.width(8.dp))
        StepBtn("+", onPlus)
    }
}

@Composable
private fun StepBtn(glyph: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(Heat.surfaceDeep, CircleShape)
            .border(1.dp, Heat.borderStrong, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = glyph, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Heat.text)
    }
}

/** HEAT pill toggle: on = orange, off = slate (mock's switch styling). */
@Composable
private fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, indent: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indent) 12.dp else 0.dp, top = 3.dp, bottom = 3.dp)
            .background(Heat.surface, RoundedCornerShape(16.dp))
            .border(1.dp, Heat.border, RoundedCornerShape(16.dp))
            .clickable { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontFamily = Heat.sairaCondensed,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Heat.text,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(24.dp)
                .background(if (checked) Heat.brandOrange else Heat.borderStrong, RoundedCornerShape(12.dp)),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(18.dp)
                    .background(if (checked) Color.White else Heat.textMuted, CircleShape),
            )
        }
    }
}

@Composable
private fun GpsPin() {
    androidx.compose.foundation.Canvas(Modifier.size(16.dp)) {
        val w = size.width; val h = size.height
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(w / 2f, h)
            cubicTo(w * 0.1f, h * 0.55f, w * 0.08f, h * 0.42f, w * 0.08f, h * 0.35f)
            cubicTo(w * 0.08f, h * 0.05f, w * 0.92f, h * 0.05f, w * 0.92f, h * 0.35f)
            cubicTo(w * 0.92f, h * 0.42f, w * 0.9f, h * 0.55f, w / 2f, h)
            close()
        }
        drawPath(p, Heat.infoBlue, style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.12f))
        drawCircle(Heat.infoBlue, radius = w * 0.12f, center = androidx.compose.ui.geometry.Offset(w / 2f, h * 0.33f))
    }
}

@Composable
private fun BarsIcon() {
    androidx.compose.foundation.Canvas(Modifier.size(16.dp)) {
        val w = size.width; val h = size.height
        val stroke = w * 0.13f
        for (frac in listOf(0.2f, 0.5f, 0.8f)) {
            drawLine(
                Heat.textMuted,
                start = androidx.compose.ui.geometry.Offset(0f, h * frac),
                end = androidx.compose.ui.geometry.Offset(w, h * frac),
                strokeWidth = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}
