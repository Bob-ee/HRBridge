package com.example.runh10.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import com.example.runh10.shared.design.HeatTokens
import com.example.runh10.shared.R as SharedR

/** HEAT design-language palette + typography for the watch app. */
object Heat {
    val bg = Color(HeatTokens.BG)
    val surface = Color(HeatTokens.SURFACE)
    val surfaceDeep = Color(HeatTokens.SURFACE_DEEP)
    val border = Color(HeatTokens.BORDER)
    val borderStrong = Color(HeatTokens.BORDER_STRONG)
    val hairline = Color(HeatTokens.HAIRLINE)

    val text = Color(HeatTokens.TEXT)
    val textClock = Color(HeatTokens.TEXT_CLOCK)
    val textMuted = Color(HeatTokens.TEXT_MUTED)
    val textDim = Color(HeatTokens.TEXT_DIM)
    val textFaint = Color(HeatTokens.TEXT_FAINT)

    val brandRed = Color(HeatTokens.BRAND_RED)
    val brandOrange = Color(HeatTokens.BRAND_ORANGE)
    val hrvPurple = Color(HeatTokens.HRV_PURPLE)
    val goodGreen = Color(HeatTokens.GOOD_GREEN)
    val infoBlue = Color(HeatTokens.INFO_BLUE)
    val danger = Color(HeatTokens.DANGER)

    val zones = HeatTokens.ZONES.map { Color(it) }
    val zoneNames = HeatTokens.ZONE_NAMES

    /** Primary-button / FAB / START gradient. */
    val brandGradient = Brush.linearGradient(listOf(brandRed, brandOrange))

    val saira = FontFamily(
        Font(SharedR.font.saira_regular, FontWeight.Normal),
        Font(SharedR.font.saira_medium, FontWeight.Medium),
        Font(SharedR.font.saira_semibold, FontWeight.SemiBold),
        Font(SharedR.font.saira_bold, FontWeight.Bold),
    )

    /** The numerics/headings workhorse — nearly every number is Condensed 800. */
    val sairaCondensed = FontFamily(
        Font(SharedR.font.saira_condensed_medium, FontWeight.Medium),
        Font(SharedR.font.saira_condensed_semibold, FontWeight.SemiBold),
        Font(SharedR.font.saira_condensed_bold, FontWeight.Bold),
        Font(SharedR.font.saira_condensed_extrabold, FontWeight.ExtraBold),
    )
}

private val heatColors = Colors(
    primary = Heat.brandOrange,
    primaryVariant = Heat.brandRed,
    secondary = Heat.infoBlue,
    background = Heat.bg,
    surface = Heat.surface,
    error = Heat.danger,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Heat.text,
    onSurface = Heat.text,
    onError = Color.White,
)

@Composable
fun HeatTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = heatColors, content = content)
}
