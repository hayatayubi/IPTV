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

/** "20:00" or "8:00 PM", following the device's locale. */
fun formatTime(epochMillis: Long): String =
    timeFormat.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

/** "20:00 – 21:00". */
fun Programme.timeRange(): String = "${formatTime(start)} – ${formatTime(stop)}"

private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

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
