package com.hayat.lumen.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Dark-only palette. Screens sit on a softly coloured [Ambient] background (not flat black), and
 * bars, buttons and panels are frosted glass over it: [Glass] fill with a faint [GlassEdge].
 * Focus is solid white with dark text. Blue is a small accent only (ticks, the update dot).
 */
object LumenColors {
    val Background = Color(0xFF0D0F14)
    /** Dialogs and other things that float over busy content need to be opaque. */
    val Surface = Color(0xFF1E2129)
    val SurfaceFocused = Color(0xFF2A2E38)

    val Glass = Color.White.copy(alpha = 0.07f)
    /** The open tab, the selected row. */
    val GlassStrong = Color.White.copy(alpha = 0.13f)
    val GlassEdge = Color.White.copy(alpha = 0.10f)

    /** The default ambient glows: a cool blue top left, a warm amber bottom right (Chillio-like). */
    val AmbientCool = Color(0xFF26406E)
    val AmbientWarm = Color(0xFF5C3B1F)

    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFFA1A1A6)
    val TextTertiary = Color(0xFF6E6E73)

    val Accent = Color(0xFF0A84FF)
}
