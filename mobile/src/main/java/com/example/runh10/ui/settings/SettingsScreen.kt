package com.example.runh10.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.runh10.data.AthleteProfile
import com.example.runh10.data.AthleteStore
import com.example.runh10.record.PhoneRecordController
import com.example.runh10.ui.components.Fmt
import com.example.runh10.ui.components.HeatToggle
import com.example.runh10.ui.components.PulseLogo
import com.example.runh10.ui.components.SectionLabel
import com.example.runh10.ui.theme.Heat
import kotlinx.coroutines.launch

/** Settings: units, heart rate, sensors, data & sync, audio coaching. */
@Composable
fun SettingsScreen(
    onMeasureResting: () -> Unit,
    onBack: () -> Unit,
    syncedAgoMs: Long?,
    bottomPadding: PaddingValues,
) {
    val context = LocalContext.current
    val store = remember { AthleteStore(context) }
    val profile = store.profile.collectAsState(initial = AthleteProfile()).value
    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .background(Heat.bg)
            .padding(top = bottomPadding.calculateTopPadding())
            .verticalScroll(rememberScrollState())
            .padding(bottom = bottomPadding.calculateBottomPadding())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹",
                fontSize = 26.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp),
            )
            Text("Settings", fontFamily = Heat.sairaCondensed, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = Heat.text)
        }
        Spacer(Modifier.height(18.dp))

        // ── Units ──
        SectionLabel("UNITS", color = Heat.textDim)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Heat.surface)
                .border(1.dp, Heat.border, RoundedCornerShape(14.dp))
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            UnitOption("MILES", profile.unitsMiles, Modifier.weight(1f)) { scope.launch { store.setUnitsMiles(true) } }
            UnitOption("KILOMETERS", !profile.unitsMiles, Modifier.weight(1f)) { scope.launch { store.setUnitsMiles(false) } }
        }
        Spacer(Modifier.height(22.dp))

        // ── Heart rate ──
        SectionLabel("HEART RATE", color = Heat.textDim)
        Spacer(Modifier.height(8.dp))
        SettingsGroup {
            SettingsRow(
                icon = { WaveIcon(Heat.infoBlue) },
                iconTint = Heat.infoBlue,
                title = "Resting heart rate",
                subtitle = "Last measured · ${Fmt.agoLabel(profile.restingMeasuredAtMs)} · tap to measure",
                onClick = onMeasureResting,
                trailing = {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            profile.restingHr?.toString() ?: "—",
                            fontFamily = Heat.sairaCondensed,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp,
                            color = Heat.infoBlue,
                        )
                        Text("bpm", fontSize = 12.sp, color = Heat.textMuted, modifier = Modifier.padding(start = 4.dp, bottom = 3.dp))
                    }
                },
            )
            HairlineRow()
            var editingMax by remember { mutableStateOf(false) }
            SettingsRow(
                icon = { PulseLogo(20.dp) },
                iconTint = Heat.danger,
                title = "Max heart rate",
                subtitle = profile.maxHr?.let { "$it bpm" } ?: "not set",
                onClick = { editingMax = !editingMax },
                trailing = { Chevron() },
            )
            if (editingMax) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Stepper("−") { scope.launch { store.setMaxHr(((profile.maxHr ?: 185) - 1).coerceAtLeast(120)) } }
                    Text(
                        profile.maxHr?.toString() ?: "185",
                        fontFamily = Heat.sairaCondensed,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = Heat.brandOrange,
                        modifier = Modifier.weight(1f),
                    )
                    Stepper("+") { scope.launch { store.setMaxHr(((profile.maxHr ?: 185) + 1).coerceAtMost(230)) } }
                }
            }
            HairlineRow()
            SettingsRow(
                icon = null,
                title = "Auto-update overnight",
                subtitle = autoRestingSubtitle(profile),
                trailing = {
                    HeatToggle(profile.autoUpdateResting) { v -> scope.launch { store.setAutoUpdateResting(v) } }
                },
            )
        }
        Spacer(Modifier.height(22.dp))

        // ── Body ──
        // Drives the HR-based calorie estimate (Keytel) — none of these are shown
        // elsewhere, so incomplete profile just means "—" on the run detail's calorie row.
        SectionLabel("BODY", color = Heat.textDim)
        Spacer(Modifier.height(8.dp))
        SettingsGroup {
            var editingWeight by remember { mutableStateOf(false) }
            SettingsRow(
                icon = null,
                title = "Weight",
                subtitle = "For calorie estimates",
                onClick = { editingWeight = !editingWeight },
                trailing = {
                    Text(
                        profile.weightKg?.let { "${it.roundToOneDecimal()} kg" } ?: "not set",
                        fontFamily = Heat.sairaCondensed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Heat.textMuted,
                    )
                },
            )
            if (editingWeight) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Stepper("−") {
                        scope.launch { store.setWeightKg(((profile.weightKg ?: 70.0) - 0.5).coerceAtLeast(30.0)) }
                    }
                    Text(
                        "${(profile.weightKg ?: 70.0).roundToOneDecimal()} kg",
                        fontFamily = Heat.sairaCondensed,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = Heat.brandOrange,
                        modifier = Modifier.weight(1f),
                    )
                    Stepper("+") {
                        scope.launch { store.setWeightKg(((profile.weightKg ?: 70.0) + 0.5).coerceAtMost(200.0)) }
                    }
                }
            }
            HairlineRow()
            var editingBirthYear by remember { mutableStateOf(false) }
            val thisYear = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }
            SettingsRow(
                icon = null,
                title = "Birth year",
                subtitle = "For calorie estimates",
                onClick = { editingBirthYear = !editingBirthYear },
                trailing = {
                    Text(
                        profile.birthYear?.toString() ?: "not set",
                        fontFamily = Heat.sairaCondensed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Heat.textMuted,
                    )
                },
            )
            if (editingBirthYear) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Stepper("−") {
                        scope.launch { store.setBirthYear(((profile.birthYear ?: (thisYear - 30)) - 1).coerceAtLeast(thisYear - 100)) }
                    }
                    Text(
                        (profile.birthYear ?: (thisYear - 30)).toString(),
                        fontFamily = Heat.sairaCondensed,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = Heat.brandOrange,
                        modifier = Modifier.weight(1f),
                    )
                    Stepper("+") {
                        scope.launch { store.setBirthYear(((profile.birthYear ?: (thisYear - 30)) + 1).coerceAtMost(thisYear - 5)) }
                    }
                }
            }
            HairlineRow()
            SettingsRow(
                icon = null,
                title = "Sex",
                subtitle = "For calorie estimates",
                trailing = {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Heat.surfaceDeep)
                            .border(1.dp, Heat.border, RoundedCornerShape(10.dp))
                            .padding(4.dp),
                    ) {
                        SexOption("MALE", profile.sexMale == true) { scope.launch { store.setSexMale(true) } }
                        SexOption("FEMALE", profile.sexMale == false) { scope.launch { store.setSexMale(false) } }
                    }
                },
            )
        }
        Spacer(Modifier.height(22.dp))

        // ── Sensors ──
        SectionLabel("SENSORS", color = Heat.textDim)
        Spacer(Modifier.height(8.dp))
        val remembered by PhoneRecordController.rememberedDevice.collectAsState()
        SettingsGroup {
            SettingsRow(
                icon = { PulseLogo(20.dp) },
                iconTint = Heat.brandRed,
                title = remembered?.name ?: "Polar H10",
                subtitle = if (remembered != null) "Paired · tap to forget" else "Pair from the record screen",
                onClick = { if (remembered != null) PhoneRecordController.forgetDevice() },
                trailing = {
                    Box(
                        Modifier.size(8.dp).background(
                            if (remembered != null) Heat.goodGreen else Heat.textFaint, CircleShape,
                        ),
                    )
                },
            )
        }
        Spacer(Modifier.height(22.dp))

        // ── Data & sync ──
        SectionLabel("DATA & SYNC", color = Heat.textDim)
        Spacer(Modifier.height(8.dp))
        SettingsGroup {
            SettingsRow(
                icon = { CheckGlyph() },
                iconTint = Heat.goodGreen,
                title = "Health Connect",
                subtitle = "Synced ${Fmt.agoLabel(syncedAgoMs)}",
                trailing = { Chevron() },
            )
        }
        Spacer(Modifier.height(22.dp))

        // ── Audio coaching ──
        SectionLabel("AUDIO COACHING", color = Heat.textDim)
        Spacer(Modifier.height(8.dp))
        SettingsGroup {
            SettingsRow(
                icon = null, title = "Voice coach", subtitle = "Pace & zone cues",
                trailing = { HeatToggle(profile.voiceCoach) { v -> scope.launch { store.setVoiceCoach(v) } } },
            )
            HairlineRow()
            SettingsRow(
                icon = null, title = "Mile announcements", subtitle = "Split recap each mile",
                trailing = { HeatToggle(profile.mileAnnouncements) { v -> scope.launch { store.setMileAnnouncements(v) } } },
            )
            HairlineRow()
            SettingsRow(
                icon = null, title = "Auto-pause", subtitle = "Pause when you stop moving",
                trailing = { HeatToggle(profile.autoPause) { v -> scope.launch { store.setAutoPause(v) } } },
            )
        }
        Spacer(Modifier.height(30.dp))
    }
}

private fun Double.roundToOneDecimal(): String = String.format(java.util.Locale.US, "%.1f", this)

/** Honest state for the "Auto-update overnight" row: off is plain; on reflects the last
 * checkOnce outcome so a Fitbit-sync gap doesn't silently look like it's working. */
private fun autoRestingSubtitle(profile: AthleteProfile): String = when {
    !profile.autoUpdateResting -> "From watch sleep & HRV data"
    profile.lastRestingAutoCheckHadData == false -> "No resting-HR data in Health Connect — enable Fitbit sync"
    profile.restingSource == "auto" && profile.restingMeasuredAtMs != null ->
        "Updated ${Fmt.agoLabel(profile.restingMeasuredAtMs)} from Health Connect"
    else -> "From watch sleep & HRV data"
}

@Composable
private fun SexOption(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .let { if (active) it.background(Heat.brandGradient) else it }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = Heat.sairaCondensed,
            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 0.4.sp,
            color = if (active) Color.White else Heat.textMuted,
        )
    }
}

@Composable
private fun UnitOption(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .let { if (active) it.background(Heat.brandGradient) else it }
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = Heat.sairaCondensed,
            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Bold,
            fontSize = 14.sp,
            letterSpacing = 0.6.sp,
            color = if (active) Color.White else Heat.textMuted,
        )
    }
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Heat.surface)
            .border(1.dp, Heat.border, RoundedCornerShape(16.dp)),
    ) { content() }
}

@Composable
private fun SettingsRow(
    icon: (@Composable () -> Unit)?,
    title: String,
    subtitle: String,
    iconTint: Color = Heat.textMuted,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(40.dp).background(iconTint.copy(alpha = 0.12f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) { icon() }
            Spacer(Modifier.width(13.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Heat.sairaCondensed, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Heat.text)
            Text(subtitle, fontSize = 12.sp, color = Heat.textDim, modifier = Modifier.padding(top = 2.dp))
        }
        trailing()
    }
}

@Composable
private fun HairlineRow() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Heat.hairline))
}

@Composable
private fun Chevron() {
    Text("›", fontSize = 20.sp, color = Heat.textFaint, fontWeight = FontWeight.Bold)
}

@Composable
private fun Stepper(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .background(Heat.surfaceDeep, CircleShape)
            .border(1.dp, Heat.borderStrong, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(glyph, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Heat.text) }
}

@Composable
private fun WaveIcon(color: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(21.dp)) {
        val w = size.width; val h = size.height
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, h * 0.5f)
            lineTo(w * 0.18f, h * 0.5f)
            lineTo(w * 0.30f, h * 0.25f)
            lineTo(w * 0.52f, h * 0.78f)
            lineTo(w * 0.64f, h * 0.5f)
            lineTo(w, h * 0.5f)
        }
        drawPath(
            p, color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = w * 0.1f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
    }
}

@Composable
private fun CheckGlyph() {
    androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
        val w = size.width; val h = size.height
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.15f, h * 0.55f)
            lineTo(w * 0.42f, h * 0.8f)
            lineTo(w * 0.88f, h * 0.22f)
        }
        drawPath(
            p, Color(0xFF34C759),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = w * 0.12f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
    }
}
