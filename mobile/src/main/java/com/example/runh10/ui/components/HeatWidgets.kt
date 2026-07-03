package com.example.runh10.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.runh10.ui.theme.Heat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ───────────────────────── formatters ─────────────────────────

object Fmt {
    private const val MILE = 1609.344
    private const val KM = 1000.0

    fun dist(m: Double, miles: Boolean): String =
        String.format(Locale.US, "%.2f", m / if (miles) MILE else KM)

    fun distUnit(miles: Boolean) = if (miles) "mi" else "km"

    fun pace(mps: Double?, miles: Boolean): String {
        if (mps == null || mps < 0.1) return "—"
        val secPer = ((if (miles) MILE else KM) / mps).toInt()
        return String.format(Locale.US, "%d:%02d", secPer / 60, secPer % 60)
    }

    fun paceUnit(miles: Boolean) = if (miles) "/mi" else "/km"

    /** 52:18 or 1:07:42 */
    fun duration(sec: Long): String {
        val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    /** "2:31 hr" style for weekly totals. */
    fun hoursShort(ms: Long): String {
        val totalMin = ms / 60_000
        return String.format(Locale.US, "%d:%02d", totalMin / 60, totalMin % 60)
    }

    /** "Yesterday · 6:14 PM", "Tue · 7:02 AM", "Jun 24 · 6:14 PM" */
    fun whenLabel(ms: Long): String {
        val time = SimpleDateFormat("h:mm a", Locale.US).format(Date(ms))
        val cal = Calendar.getInstance().apply { timeInMillis = ms }
        val today = Calendar.getInstance()
        val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        fun sameDay(a: Calendar, b: Calendar) =
            a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        val day = when {
            sameDay(cal, today) -> "Today"
            sameDay(cal, yesterday) -> "Yesterday"
            today.timeInMillis - ms < 6L * 86_400_000 -> SimpleDateFormat("EEE", Locale.US).format(Date(ms))
            else -> SimpleDateFormat("MMM d", Locale.US).format(Date(ms))
        }
        return "$day · $time"
    }

    /** "WED JUN 24" chip prefix. */
    fun chipDate(ms: Long): String =
        SimpleDateFormat("EEE MMM d", Locale.US).format(Date(ms)).uppercase(Locale.US)

    fun agoLabel(ms: Long?): String {
        if (ms == null) return "never"
        val d = System.currentTimeMillis() - ms
        return when {
            d < 90_000 -> "just now"
            d < 3_600_000 -> "${d / 60_000} min ago"
            d < 86_400_000 -> "${d / 3_600_000} hr ago"
            else -> "${d / 86_400_000} days ago"
        }
    }
}

// ───────────────────────── building blocks ─────────────────────────

/** Standard HEAT card: surface bg, 1px border, 20dp radius. */
@Composable
fun HeatCard(
    modifier: Modifier = Modifier,
    radius: Dp = 20.dp,
    background: Color = Heat.surface,
    borderColor: Color = Heat.border,
    padding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .background(background, RoundedCornerShape(radius))
            .border(1.dp, borderColor, RoundedCornerShape(radius))
            .padding(padding),
    ) { content() }
}

/** "THIS WEEK"-style tracking-caps section label. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Heat.textMuted) {
    Text(
        text = text,
        modifier = modifier,
        fontFamily = Heat.sairaCondensed,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 1.8.sp,
        color = color,
    )
}

/** Translucent color chip ("TEMPO", "ZONE 4 · THRESHOLD"). */
@Composable
fun HeatChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            fontFamily = Heat.sairaCondensed,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.1.sp,
            color = color,
        )
    }
}

/** Big condensed stat with a caps label under it. */
@Composable
fun StatBlock(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Heat.text,
    valueSize: Int = 26,
    unit: String? = null,
    align: Alignment.Horizontal = Alignment.Start,
) {
    Column(modifier = modifier, horizontalAlignment = align) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontFamily = Heat.sairaCondensed,
                fontWeight = FontWeight.ExtraBold,
                fontSize = valueSize.sp,
                lineHeight = valueSize.sp,
                color = valueColor,
            )
            if (unit != null) {
                Text(
                    text = unit,
                    fontSize = 12.sp,
                    color = Heat.textMuted,
                    modifier = Modifier.padding(start = 3.dp, bottom = 2.dp),
                )
            }
        }
        Text(
            text = label,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            color = Heat.textDim,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/** Brand-gradient primary button. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .background(Heat.brandGradient, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontFamily = Heat.sairaCondensed,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 17.sp,
            letterSpacing = 1.sp,
            color = Color.White,
        )
    }
}

/** HEAT pill toggle (on = orange). */
@Composable
fun HeatToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .width(46.dp)
            .height(27.dp)
            .background(if (checked) Heat.brandOrange else Heat.borderStrong, RoundedCornerShape(14.dp))
            .clickable { onChange(!checked) },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(horizontal = 3.dp)
                .size(21.dp)
                .background(if (checked) Color.White else Heat.textMuted, CircleShape),
        )
    }
}

/** The HR Bridge pulse-waveform logo mark (no bridges here). */
@Composable
fun PulseLogo(size: Dp, color: Color = Heat.brandRed, strokeWidth: Dp = 2.4.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension / 24f
        val p = Path().apply {
            moveTo(2f * s, 12f * s)
            lineTo(6f * s, 12f * s)
            lineTo(8.5f * s, 19f * s)
            lineTo(12.5f * s, 4f * s)
            lineTo(15.5f * s, 15f * s)
            lineTo(17f * s, 11f * s)
            lineTo(19f * s, 12f * s)
            lineTo(23f * s, 12f * s)
        }
        drawPath(
            p, color,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** Row of stats separated by 1px vertical rules (run cards, stat sheets). */
@Composable
fun DividedStatsRow(
    modifier: Modifier = Modifier,
    stats: List<Triple<String, String, Color?>>, // value, label, color
    valueSize: Int = 26,
    unitOf: (Int) -> String? = { null },
    centered: Boolean = false,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        stats.forEachIndexed { i, (value, label, color) ->
            Box(
                Modifier
                    .weight(1f)
                    .let { if (i > 0) it.leftHairline() else it }
                    .padding(start = if (i > 0) 14.dp else 0.dp),
            ) {
                StatBlock(
                    value = value,
                    label = label,
                    valueColor = color ?: Heat.text,
                    valueSize = valueSize,
                    unit = unitOf(i),
                    align = if (centered) Alignment.CenterHorizontally else Alignment.Start,
                    modifier = if (centered) Modifier.fillMaxWidth() else Modifier,
                )
            }
        }
    }
}

private fun Modifier.leftHairline(): Modifier = this.drawBehind {
    drawLine(
        color = Color(com.example.runh10.shared.design.HeatTokens.BORDER),
        start = Offset(0f, 0f),
        end = Offset(0f, size.height),
        strokeWidth = 1.dp.toPx(),
    )
}
