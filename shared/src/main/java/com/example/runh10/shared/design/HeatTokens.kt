package com.example.runh10.shared.design

/**
 * HEAT design language tokens (Fable 5 handoff). Plain ARGB values so this module
 * needs no Compose dependency — apps wrap them in Color(...).
 */
object HeatTokens {
    // Surfaces
    const val BG = 0xFF0A0C0F
    const val SURFACE = 0xFF12161B
    const val SURFACE_DEEP = 0xFF0C1014
    const val BORDER = 0xFF1E242C
    const val BORDER_STRONG = 0xFF2A323C
    const val HAIRLINE = 0xFF1A2027

    // Text
    const val TEXT = 0xFFE8ECF1
    const val TEXT_CLOCK = 0xFFC6CDD6
    const val TEXT_MUTED = 0xFF8A94A1
    const val TEXT_DIM = 0xFF6B7280
    const val TEXT_FAINT = 0xFF5A6470

    // Brand & accents
    const val BRAND_RED = 0xFFE11D2E
    const val BRAND_ORANGE = 0xFFF97316
    const val HRV_PURPLE = 0xFFA78BFA
    const val HRV_LABEL = 0xFF9D8FD6
    const val HRV_CARD_TOP = 0xFF1C1730
    const val HRV_BORDER = 0xFF2A2440
    const val GOOD_GREEN = 0xFF34C759
    const val INFO_BLUE = 0xFF2E9BE6
    const val DANGER = 0xFFEF4444

    // HR zones Z1..Z5
    const val Z1 = 0xFF6E8BA3
    const val Z2 = 0xFF2E9BE6
    const val Z3 = 0xFF22C55E
    const val Z4 = 0xFFF59E0B
    const val Z5 = 0xFFEF4444
    val ZONES = listOf(Z1, Z2, Z3, Z4, Z5)
    val ZONE_NAMES = listOf("Recovery", "Easy", "Aerobic", "Threshold", "Max")

    /** Effort/route heatmap gradient stops, easy → hard. */
    val HEAT_GRADIENT = listOf(Z2, Z3, Z4, Z5)
}
