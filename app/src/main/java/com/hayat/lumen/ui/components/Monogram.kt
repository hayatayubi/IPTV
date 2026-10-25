package com.hayat.lumen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hayat.lumen.ui.theme.LumenColors

/** Fallback artwork: channel initials on a muted colour derived from the name. Never a broken image. */
@Composable
fun Monogram(name: String, size: Dp, modifier: Modifier = Modifier) {
    val initials = remember(name) { Monograms.initials(name) }
    val color = remember(name) { Monograms.colorFor(name) }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.2f))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = (size.value * 0.36f).sp),
            color = LumenColors.TextPrimary,
            maxLines = 1,
        )
    }
}

object Monograms {
    private val palette = listOf(
        Color(0xFF3A4A5C), Color(0xFF4A3F5C), Color(0xFF5C3F4A), Color(0xFF5C4F3A),
        Color(0xFF3F5C4A), Color(0xFF3A555C), Color(0xFF4F4F55), Color(0xFF5C463A),
    )

    // Strips the country/quality tags IPTV names are full of: "UK: ", "|UK| ", "[US] ", "US - ".
    private val prefix = Regex("""^\s*(\[[^]]*]|\|[^|]*\||[A-Za-z]{2,3}\s*[:|\-])\s*""")
    private val wordChars = Regex("[^\\p{L}\\p{N} ]")

    fun initials(name: String): String {
        val words = name.replace(prefix, "").replace(wordChars, " ").split(' ').filter { it.isNotBlank() }
        return when {
            words.isEmpty() -> "•"
            words.size == 1 -> words[0].take(2).uppercase()
            else -> "${words[0].first()}${words[1].first()}".uppercase()
        }
    }

    fun colorFor(name: String): Color = palette[Math.floorMod(name.hashCode(), palette.size)]
}
