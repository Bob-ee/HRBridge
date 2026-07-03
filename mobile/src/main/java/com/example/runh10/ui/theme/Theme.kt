package com.example.runh10.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.runh10.shared.design.HeatTokens
import com.example.runh10.shared.R as SharedR

/** HEAT palette + typography for the phone app (dark instrument-cluster; no light theme). */
object Heat {
    val bg = Color(HeatTokens.BG)
    val surface = Color(HeatTokens.SURFACE)
    val surfaceDeep = Color(HeatTokens.SURFACE_DEEP)
    val border = Color(HeatTokens.BORDER)
    val borderStrong = Color(HeatTokens.BORDER_STRONG)
    val hairline = Color(HeatTokens.HAIRLINE)

    val text = Color(HeatTokens.TEXT)
    val textMuted = Color(HeatTokens.TEXT_MUTED)
    val textDim = Color(HeatTokens.TEXT_DIM)
    val textFaint = Color(HeatTokens.TEXT_FAINT)

    val brandRed = Color(HeatTokens.BRAND_RED)
    val brandOrange = Color(HeatTokens.BRAND_ORANGE)
    val hrvPurple = Color(HeatTokens.HRV_PURPLE)
    val hrvLabel = Color(HeatTokens.HRV_LABEL)
    val hrvCardTop = Color(HeatTokens.HRV_CARD_TOP)
    val hrvBorder = Color(HeatTokens.HRV_BORDER)
    val goodGreen = Color(HeatTokens.GOOD_GREEN)
    val infoBlue = Color(HeatTokens.INFO_BLUE)
    val danger = Color(HeatTokens.DANGER)

    val zones = HeatTokens.ZONES.map { Color(it) }
    val zoneNames = HeatTokens.ZONE_NAMES
    val heatGradientColors = HeatTokens.HEAT_GRADIENT.map { Color(it) }

    val brandGradient = Brush.linearGradient(listOf(brandRed, brandOrange))

    val saira = FontFamily(
        Font(SharedR.font.saira_regular, FontWeight.Normal),
        Font(SharedR.font.saira_medium, FontWeight.Medium),
        Font(SharedR.font.saira_semibold, FontWeight.SemiBold),
        Font(SharedR.font.saira_bold, FontWeight.Bold),
    )

    val sairaCondensed = FontFamily(
        Font(SharedR.font.saira_condensed_medium, FontWeight.Medium),
        Font(SharedR.font.saira_condensed_semibold, FontWeight.SemiBold),
        Font(SharedR.font.saira_condensed_bold, FontWeight.Bold),
        Font(SharedR.font.saira_condensed_extrabold, FontWeight.ExtraBold),
    )

    fun zoneColor(zone: Int?): Color = zone?.let { zones[(it - 1).coerceIn(0, 4)] } ?: textFaint
    fun zoneName(zone: Int?): String = zone?.let { zoneNames[(it - 1).coerceIn(0, 4)] } ?: "—"
}

private val heatScheme = darkColorScheme(
    primary = Heat.brandOrange,
    onPrimary = Color.White,
    secondary = Heat.infoBlue,
    background = Heat.bg,
    onBackground = Heat.text,
    surface = Heat.surface,
    onSurface = Heat.text,
    surfaceVariant = Heat.surfaceDeep,
    onSurfaceVariant = Heat.textMuted,
    outline = Heat.border,
    error = Heat.danger,
)

private val heatTypography = Typography(
    bodyLarge = TextStyle(fontFamily = Heat.saira, fontSize = 15.sp, color = Heat.text),
    bodyMedium = TextStyle(fontFamily = Heat.saira, fontSize = 13.sp, color = Heat.text),
    bodySmall = TextStyle(fontFamily = Heat.saira, fontSize = 11.sp, color = Heat.textMuted),
    titleLarge = TextStyle(fontFamily = Heat.sairaCondensed, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, color = Heat.text),
    titleMedium = TextStyle(fontFamily = Heat.sairaCondensed, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Heat.text),
    labelSmall = TextStyle(fontFamily = Heat.sairaCondensed, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.8.sp, color = Heat.textMuted),
)

@Composable
fun HeatTheme(content: @Composable () -> Unit) {
    // HEAT is dark-only by design; ignore system light mode.
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = heatScheme, typography = heatTypography, content = content)
}
