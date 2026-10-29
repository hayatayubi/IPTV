package com.hayat.lumen.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hayat.lumen.data.epg.Programme
import com.hayat.lumen.ui.components.ChannelLogo
import com.hayat.lumen.ui.components.ProgrammeProgress
import com.hayat.lumen.ui.components.QualityTag
import com.hayat.lumen.ui.components.dayName
import com.hayat.lumen.ui.components.formatDuration
import com.hayat.lumen.ui.components.formatTime
import com.hayat.lumen.ui.components.glass
import com.hayat.lumen.ui.components.timeRange
import com.hayat.lumen.ui.theme.LumenColors
import com.hayat.lumen.ui.theme.SafeArea
import com.hayat.lumen.util.ChannelNames

/** Which programme the guide strip selects. */
object GuidePick {
    /**
     * The programme on at [time], else the first one after it, else the last; -1 when there are none.
     * Keeps the strip at the same time of day while Up/Down moves between channels.
     */
    fun index(programmes: List<Programme>, time: Long): Int {
        if (programmes.isEmpty()) return -1
        programmes.indexOfFirst { time >= it.start && time < it.stop }.takeIf { it >= 0 }?.let { return it }
        programmes.indexOfFirst { it.start >= time }.takeIf { it >= 0 }?.let { return it }
        return programmes.lastIndex
    }
}

/**
 * The guide inside the player: a glass strip over the bottom of the video with one channel's
 * programmes from now on. The video keeps playing behind it; the player handles the keys.
 */
@Composable
fun GuideStrip(browsed: Browsed, schedule: List<Programme>?, selected: Int, now: Long) {
    val channel = browsed.channel
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
            .padding(horizontal = SafeArea.Horizontal, vertical = SafeArea.Vertical)
            .padding(top = 96.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(28.dp), fill = LumenColors.Background.copy(alpha = 0.78f))
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                ChannelLogo(channel.name, channel.logoUrl, size = 56.dp)
                val name = remember(channel.name) { ChannelNames.display(channel.name) }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        channel.number?.let {
                            Text(it.toString(), style = MaterialTheme.typography.titleLarge, color = LumenColors.TextSecondary)
                        }
                        Text(
                            name.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = LumenColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        name.tags.forEach { QualityTag(it) }
                    }
                    Text(
                        if (browsed.isTuned) "Watching now" else "Press OK to watch",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (browsed.isTuned) LumenColors.TextSecondary else LumenColors.TextPrimary,
                    )
                }
                // The channels Up and Down lead to, so it's clear the strip can move.
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 260.dp)) {
                    browsed.previous?.let { Neighbour("▲", it.name) }
                    browsed.next?.let { Neighbour("▼", it.name) }
                }
            }
            Spacer(Modifier.height(16.dp))
            when {
                schedule == null -> Message("Loading the guide…")
                schedule.isEmpty() -> Message("No guide info for this channel")
                else -> Programmes(schedule, selected, now, channelKey = channel.id)
            }
        }
    }
}

@Composable
private fun Neighbour(arrow: String, name: String) {
    Text(
        "$arrow  ${ChannelNames.display(name).title}",
        style = MaterialTheme.typography.labelMedium,
        color = LumenColors.TextTertiary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Message(text: String) {
    // Same height as the programme cards and details, so the strip doesn't jump as the guide loads.
    Box(Modifier.fillMaxWidth().height(ProgrammesHeight), contentAlignment = Alignment.CenterStart) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = LumenColors.TextSecondary)
    }
}

@Composable
private fun Programmes(schedule: List<Programme>, selected: Int, now: Long, channelKey: Long) {
    val row = rememberLazyListState()
    // A new channel jumps straight to the selection; moving along the same channel slides.
    LaunchedEffect(channelKey) { row.scrollToItem((selected - 1).coerceAtLeast(0)) }
    LaunchedEffect(selected) { row.animateScrollToItem((selected - 1).coerceAtLeast(0)) }

    Column(Modifier.height(ProgrammesHeight)) {
        // Not focusable: the player's key handler moves the selection (like the guide grid).
        LazyRow(state = row, userScrollEnabled = false, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(schedule) { index, programme ->
                ProgrammeCard(programme, selected = index == selected, now = now)
            }
        }
        val shown = schedule.getOrNull(selected) ?: return@Column
        Spacer(Modifier.height(14.dp))
        Text(
            buildString {
                append(shown.title).append("  ·  ")
                dayName(shown.start, now)?.let { append(it).append(' ') }
                append(shown.timeRange()).append("  ·  ")
                if (now >= shown.start) append(formatDuration(shown.stop - now)).append(" left")
                else append(formatDuration(shown.stop - shown.start))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = LumenColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            shown.description?.takeIf { it.isNotBlank() } ?: "No description",
            style = MaterialTheme.typography.bodyMedium,
            color = LumenColors.TextPrimary.copy(alpha = 0.85f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProgrammeCard(programme: Programme, selected: Boolean, now: Long) {
    val onNow = now >= programme.start && now < programme.stop
    // White when selected with dark text, as focus looks everywhere else in Lumen.
    val text = if (selected) LumenColors.Background else LumenColors.TextPrimary
    val subtle = if (selected) LumenColors.Background.copy(alpha = 0.7f) else LumenColors.TextSecondary
    Column(
        Modifier
            .width(CardWidth)
            .background(if (selected) Color.White else LumenColors.GlassStrong, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            if (onNow) "Now" else listOfNotNull(dayName(programme.start, now), formatTime(programme.start)).joinToString(" "),
            style = MaterialTheme.typography.labelMedium,
            color = if (onNow && !selected) LumenColors.Accent else subtle,
        )
        Text(
            programme.title,
            style = MaterialTheme.typography.titleMedium,
            color = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        if (onNow) {
            ProgrammeProgress(programme.progress(now), color = text)
        } else {
            Spacer(Modifier.height(3.dp))
        }
    }
}

private val CardWidth = 260.dp
private val ProgrammesHeight = 158.dp
