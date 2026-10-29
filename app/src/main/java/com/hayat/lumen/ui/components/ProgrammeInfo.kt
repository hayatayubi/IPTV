package com.hayat.lumen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hayat.lumen.data.epg.Programme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** "20:00" or "8:00 PM", following the device's locale. */
fun formatTime(epochMillis: Long): String =
    timeFormat.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

/** "20:00 – 21:00". */
fun Programme.timeRange(): String = "${formatTime(start)} – ${formatTime(stop)}"

private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/** "Tomorrow", "Thursday"…, or null for today. */
fun dayName(time: Long, now: Long): String? {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when (day) {
        today -> null
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    }
}

/** "45 min", "2 h", "1 h 30 min". */
fun formatDuration(millis: Long): String {
    val minutes = (millis / 60_000).toInt()
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h h"
        else -> "$h h $m min"
    }
}

/** A thin progress bar for how far through a programme we are. */
@Composable
fun ProgrammeProgress(progress: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 3.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height))
            .background(color.copy(alpha = 0.25f)),
    ) {
        Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(color))
    }
}
