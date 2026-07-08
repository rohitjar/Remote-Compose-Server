package com.remotecompose.rc.theme

/**
 * Shared design-system color tokens (ARGB), sourced from the JAR Figma variables.
 * Single source of truth — screens and components reference these instead of inlining hex.
 */
object Colors {
    // Neutrals / text
    const val white            = 0xFFFFFFFF.toInt()
    const val textPrimary      = 0xFFFFFFFF.toInt() // ui/text/color_text_primary
    const val textSecondary    = 0xFFBEB4CC.toInt() // ui/text/color_text_secondary

    // Purple palette
    const val purple50         = 0xFFF5F3FF.toInt() // color_purple_50
    const val purple400        = 0xFF8B5CF6.toInt() // color_purple_400
    const val purple500        = 0xFF7029CC.toInt() // visual/purple/purple-500
    const val purple700        = 0xFF43197A.toInt() // visual/purple/purple-700
    const val purple900        = 0xFF160829.toInt() // visual/purple/purple-900
    const val btnPrimary       = 0xFF6B46C1.toInt() // color_btn_primary_bg

    // Backgrounds / surfaces
    const val bgApp            = 0xFF0D0D1A.toInt() // color_bg (app)
    const val bgProfile        = 0xFF201929.toInt() // ui/background/color-bg (profile surface)
    const val bgLabel          = 0xFF2D2D3A.toInt() // color_bg_label
    const val bgSurface2       = 0xFF1F1F2E.toInt() // color_bg_surface_2

    // Lines / borders / misc
    const val cardBorderLight  = 0xFFF1EAFA.toInt() // avatar bg (#F1EAFA)
    const val cardBorderBase   = 0x668D54D6         // card stroke base (#8D54D6 @ 40%)
    const val cardBorderGlow   = 0x66F1EAFA         // card stroke highlight start (#F1EAFA @ 40%)
    const val cardBorderGlowEnd = 0x00F1EAFA        // card stroke highlight end (transparent)
    const val bandBorder       = 0xFF554766.toInt() // color_bg_label (band hairline)
    const val divider          = 0x52837299         // rgba(131,114,153,0.32)
    const val ctaCaution       = 0xFF374151.toInt() // color_cta_caution_state
    const val green600         = 0xFF21A357.toInt() // verified chip (green-600)
    const val borderPrimaryTop = 0x66FFFFFF.toInt() // white @ 40% (primary/black button top border)
}
