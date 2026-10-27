package com.hayat.lumen.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.hayat.lumen.R
import com.hayat.lumen.util.ChannelNames

/**
 * Built-in artwork for the main UK channels, so they look right whatever logo the provider
 * sends (often old, low-res, or on a mismatched square). Each brand is a solid tile in its
 * own colour with a clean logo. Logos: tv-logo/tv-logos on GitHub (see third_party/tv-logos).
 *
 * [logoTint] recolours the logo (white or black on the tile); null keeps its own colours.
 */
class Brand(@DrawableRes val logo: Int, val tile: Color, val logoTint: Color? = Color.White, val logoFraction: Float = 0.5f)

object BrandLogos {
    private val skyNavy = Color(0xFF081030) // dark enough for the blue "sky" to read
    private fun sky(@DrawableRes logo: Int) = Brand(logo, skyNavy, logoTint = null, logoFraction = 0.62f)

    // First match wins, so the specific patterns (ITV2) come before the general ones (ITV).
    private val rules: List<Pair<Regex, Brand>> = listOf(
        "bbc (one|1)" to Brand(R.drawable.logo_bbc_one, Color(0xFFD21E2B)),
        "bbc (two|2)" to Brand(R.drawable.logo_bbc_two, Color(0xFF0F8A70)),
        "bbc (three|3)" to Brand(R.drawable.logo_bbc_three, Color(0xFF9DF02A), logoTint = Color(0xFF111111)),
        "bbc (four|4)" to Brand(R.drawable.logo_bbc_four, Color(0xFF6A3BD8)),
        "bbc news" to Brand(R.drawable.logo_bbc_news, Color(0xFFB80000)),
        "itv ?(2|two)" to Brand(R.drawable.logo_itv_2, Color(0xFFB0249F), logoFraction = 0.4f),
        "itv ?(3|three)" to Brand(R.drawable.logo_itv_3, Color(0xFF7E55CC), logoFraction = 0.4f),
        "itv ?(4|four)" to Brand(R.drawable.logo_itv_4, Color(0xFF16955A), logoFraction = 0.4f),
        "itv ?be" to Brand(R.drawable.logo_itv_be, Color(0xFFD06A55), logoFraction = 0.4f),
        // ITV1 and its regions ("ITV1 London", "ITV London", "ITV"), but not ITV Encore, ITVX etc.
        "itv ?(1|one)|itv( (london|anglia|central|granada|meridian|tyne tees|yorkshire|wales|west country|west|border|calendar|channel))?$" to Brand(R.drawable.logo_itv_1, Color(0xFF1A8796), logoFraction = 0.4f),
        "(channel|ch|c) ?4" to Brand(R.drawable.logo_channel_4, Color(0xFF2B2B2B), logoFraction = 0.62f),
        "e ?4" to Brand(R.drawable.logo_e_4, Color(0xFF7B45E0), logoFraction = 0.62f),
        "more ?4|4 ?more" to Brand(R.drawable.logo_more_4, Color(0xFF2F9E3F), logoFraction = 0.62f),
        "film ?4" to Brand(R.drawable.logo_film_4, Color(0xFFC8141C), logoFraction = 0.62f),
        "sky ?sports? (main ?event|me)" to sky(R.drawable.logo_sky_sports_main_event),
        "sky ?sports? (premier ?league|pl|premier)" to sky(R.drawable.logo_sky_sports_premier_league),
        "sky ?sports? football" to sky(R.drawable.logo_sky_sports_football),
        "sky ?sports? (f ?1|formula ?1)" to sky(R.drawable.logo_sky_sports_f1),
        "sky ?sports? cricket" to sky(R.drawable.logo_sky_sports_cricket),
        "sky ?sports? golf" to sky(R.drawable.logo_sky_sports_golf),
        "sky ?sports? action" to sky(R.drawable.logo_sky_sports_action),
        "sky ?sports? news" to sky(R.drawable.logo_sky_sports_news),
        "sky ?sports? mix" to sky(R.drawable.logo_sky_sports_mix),
        "sky ?sports? arena" to sky(R.drawable.logo_sky_sports_arena),
        "sky ?sports? racing" to sky(R.drawable.logo_sky_sports_racing),
        "sky ?sports? tennis" to sky(R.drawable.logo_sky_sports_tennis),
    ).map { (pattern, brand) -> Regex("^($pattern)\\b") to brand }

    private val cache = HashMap<String, Brand?>()

    /** The built-in brand for a raw playlist name, or null to use the provider's logo. */
    @Synchronized
    fun forName(rawName: String): Brand? = cache.getOrPut(rawName) {
        val key = normalise(ChannelNames.title(rawName))
        rules.firstOrNull { (pattern, _) -> pattern.containsMatchIn(key) }?.second
    }

    /** "BBC One London" → "bbc one london"; "ITV-1" → "itv 1"; "Channel4" → "channel 4". */
    internal fun normalise(name: String): String = name.lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("(?<=[a-z])(?=[0-9])"), " ") // "channel4" → "channel 4" (and "itv1" → "itv 1")
        .trim()
}
