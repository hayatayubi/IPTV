package com.hayat.lumen.ui.guide

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.hayat.lumen.LocalAppContainer
import com.hayat.lumen.ui.components.LumenButton
import com.hayat.lumen.ui.components.LumenListItem
import com.hayat.lumen.ui.components.Monogram
import com.hayat.lumen.ui.components.formatTime
import com.hayat.lumen.ui.components.timeRange
import com.hayat.lumen.ui.theme.LumenColors
import com.hayat.lumen.ui.theme.LumenMotion
import com.hayat.lumen.ui.theme.SafeArea
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val ChannelColumn = 240.dp
private val RowHeight = 64.dp
private val RowGap = 6.dp

/** Horizontal scale: half an hour is 150 dp, so a 1080p TV shows about two hours. */
private val PerMinute = 5.dp
private const val MINUTE = 60_000L

/**
 * The TV guide grid: channels down the left, the day across. Left/Right moves through a
 * channel's programmes (the time window follows), Up/Down changes channel at the same time,
 * OK plays the channel. The button at the top switches between favourites and groups.
 *
 * The grid is one focusable box that handles the D-pad itself, instead of hundreds of
 * focusable cells: that keeps Up/Down at the same time and stays light on the Chromecast.
 */
@Composable
fun GuideScreen(onPlay: (channelId: Long, inFavourites: Boolean) -> Unit, onOpenSettings: () -> Unit) {
    val container = LocalAppContainer.current
    val vm = viewModel { GuideViewModel(container.live, container.epg) }
    val options by vm.options.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val channels by vm.channels.collectAsStateWithLifecycle()
    val slots by vm.slots.collectAsStateWithLifecycle()

    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }

    val gridFocus = remember { FocusRequester() }
    var gridFocused by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = vm.row)

    val row = vm.row.coerceIn(0, (channels.size - 1).coerceAtLeast(0))
    val focusedChannel = channels.getOrNull(row)
    val focusedSlots = focusedChannel?.let { slots[it.id] }
    val focusedSlot = focusedSlots?.getOrNull(GuideSlots.indexAt(focusedSlots, vm.anchor))

    // The window position, animated. Cells read it while placing themselves (offset { }),
    // so scrolling the timeline moves them without recomposing anything.
    val windowMinutes = animateFloatAsState(
        targetValue = (vm.windowStart - vm.gridStart) / MINUTE.toFloat(),
        animationSpec = LumenMotion.focus(),
        label = "window",
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(LumenColors.Background)
            .padding(horizontal = SafeArea.Horizontal, vertical = SafeArea.Vertical),
    ) {
        Row(
            Modifier.fillMaxWidth().focusProperties { canFocus = !pickerOpen },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "TV guide",
                style = MaterialTheme.typography.headlineLarge,
                color = LumenColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            selected?.let { LumenButton(text = it.title, onClick = { pickerOpen = true }) }
            LumenButton(text = "Guide settings", onClick = onOpenSettings)
        }

        Spacer(Modifier.height(12.dp))
        Details(
            channel = focusedChannel,
            slot = focusedSlot,
            loading = focusedChannel != null && focusedSlots == null,
            now = now,
        )
        Spacer(Modifier.height(12.dp))

        when {
            options.isEmpty() || selected == null -> Message("No channels yet", "Add a playlist to see its guide here.")
            channels.isEmpty() -> Message("Nothing here", "This list has no channels right now.")
            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                // How much time fits on screen, which decides when the window moves.
                val span = ((maxWidth - ChannelColumn) / PerMinute).toLong() * MINUTE

                fun moveTo(slot: Slot) {
                    var start = vm.windowStart
                    if (slot.start < start) start = GuideSlots.floorHalfHour(slot.start)
                    else if (slot.start >= start + span - GuideSlots.HALF_HOUR) {
                        start = GuideSlots.floorHalfHour(slot.start) - GuideSlots.HALF_HOUR
                    }
                    vm.windowStart = start.coerceIn(vm.gridStart, maxOf(vm.gridStart, vm.gridEnd - span))
                    vm.anchor = maxOf(slot.start, vm.windowStart)
                }

                fun step(delta: Int): Boolean {
                    val list = focusedSlots ?: return true
                    val next = list.getOrNull(GuideSlots.indexAt(list, vm.anchor) + delta) ?: return true
                    moveTo(next)
                    return true
                }

                fun changeRow(delta: Int): Boolean {
                    val target = (row + delta).coerceIn(0, channels.lastIndex)
                    vm.row = target
                    return true
                }

                Column(
                    Modifier
                        .fillMaxSize()
                        .focusRequester(gridFocus)
                        .focusProperties { canFocus = !pickerOpen }
                        .onFocusChanged { gridFocused = it.isFocused }
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            when (event.key) {
                                Key.DirectionLeft -> step(-1)
                                Key.DirectionRight -> step(+1)
                                // Up on the first channel lets focus go up to the buttons.
                                Key.DirectionUp -> if (row == 0) false else changeRow(-1)
                                Key.DirectionDown -> changeRow(+1)
                                Key.ChannelUp, Key.PageUp -> changeRow(-PAGE)
                                Key.ChannelDown, Key.PageDown -> changeRow(+PAGE)
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                    if (event.nativeKeyEvent.repeatCount == 0) focusedChannel?.let {
                                        container.lastChannelId.value = it.id
                                        onPlay(it.id, selected?.list == GuideList.Favourites)
                                    }
                                    true
                                }
                                else -> false
                            }
                        }
                        .focusable(),
                ) {
                    TimeRuler(vm.gridStart, vm.windowStart, span, windowMinutes)
                    Box(Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            // The grid moves the list itself, following the highlight.
                            userScrollEnabled = false,
                            contentPadding = PaddingValues(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(RowGap),
                        ) {
                            itemsIndexed(channels, key = { _, c -> c.id }) { index, channel ->
                                val rowFocused = gridFocused && index == row
                                GuideRow(
                                    channel = channel,
                                    slots = slots[channel.id],
                                    focusedSlot = if (rowFocused) focusedSlot else null,
                                    highlighted = index == row,
                                    gridStart = vm.gridStart,
                                    windowStart = vm.windowStart,
                                    span = span,
                                    windowMinutes = windowMinutes,
                                    now = now,
                                )
                            }
                        }
                        NowLine(vm.gridStart, now, windowMinutes)
                    }
                }

                LaunchedEffect(row, channels.size) { listState.keepVisible(row) }
            }
        }
    }

    if (pickerOpen) {
        GroupPicker(
            options = options,
            selected = selected?.list,
            onPick = {
                vm.pick(it)
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
        )
    }

    // Once the picker closes (or channels first arrive), put focus back on the grid.
    LaunchedEffect(pickerOpen, channels.isNotEmpty()) {
        if (!pickerOpen && channels.isNotEmpty()) runCatching { gridFocus.requestFocus() }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo
            (visible.firstOrNull()?.index ?: 0)..(visible.lastOrNull()?.index ?: 0)
        }.collect(vm::setVisibleRows)
    }
}

private const val PAGE = 5

/** Scrolls just enough to show the whole of row [index]. */
private suspend fun LazyListState.keepVisible(index: Int) {
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return scrollToItem(index)
    val firstFull = visible.firstOrNull { it.offset >= 0 }?.index ?: visible.first().index
    val lastFull = visible.lastOrNull { it.offset + it.size <= info.viewportEndOffset }?.index ?: visible.last().index
    when {
        index < firstFull -> animateScrollToItem(index)
        index > lastFull -> animateScrollToItem((index - (lastFull - firstFull)).coerceAtLeast(0))
    }
}

/** The focused programme in full, above the grid. Fixed height, so the grid doesn't jump. */
@Composable
private fun Details(channel: GuideChannel?, slot: Slot?, loading: Boolean, now: Long) {
    Column(Modifier.fillMaxWidth().height(112.dp)) {
        if (channel == null) return@Column
        val programme = slot?.programme
        Text(
            programme?.title ?: if (loading) "" else "No guide info",
            style = MaterialTheme.typography.headlineSmall,
            color = LumenColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (programme != null && now in programme.start until programme.stop) {
                Text(
                    "LIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = LumenColors.TextPrimary,
                    modifier = Modifier
                        .background(LumenColors.Accent, RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
            Text(
                buildString {
                    append(channel.name)
                    if (programme != null) {
                        append("  ·  ")
                        dayName(programme.start, now)?.let { append(it).append(' ') }
                        append(programme.timeRange())
                        append("  ·  ").append(duration(programme.stop - programme.start))
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = LumenColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        programme?.description?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = LumenColors.TextTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Half-hour labels above the grid, and the day of the window on the left. */
@Composable
private fun TimeRuler(gridStart: Long, windowStart: Long, span: Long, windowMinutes: State<Float>) {
    val pxPerMinute = with(LocalDensity.current) { PerMinute.toPx() }
    Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            dayName(windowStart, System.currentTimeMillis()) ?: "Today",
            style = MaterialTheme.typography.labelLarge,
            color = LumenColors.TextPrimary,
            modifier = Modifier.width(ChannelColumn).padding(start = 4.dp),
        )
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds(), contentAlignment = Alignment.CenterStart) {
            var t = GuideSlots.floorHalfHour(windowStart - span)
            while (t < windowStart + 2 * span) {
                val minutes = (t - gridStart) / MINUTE.toFloat()
                key(t) {
                    Text(
                        formatTime(t),
                        style = MaterialTheme.typography.labelMedium,
                        color = LumenColors.TextSecondary,
                        maxLines = 1,
                        modifier = Modifier
                            .offset { IntOffset(((minutes - windowMinutes.value) * pxPerMinute).roundToInt(), 0) }
                            .padding(start = 12.dp),
                    )
                }
                t += GuideSlots.HALF_HOUR
            }
        }
    }
}

/** A thin accent line at the current time, over the rows. */
@Composable
private fun NowLine(gridStart: Long, now: Long, windowMinutes: State<Float>) {
    val pxPerMinute = with(LocalDensity.current) { PerMinute.toPx() }
    val minutes = (now - gridStart) / MINUTE.toFloat()
    Box(Modifier.fillMaxSize().padding(start = ChannelColumn).clipToBounds()) {
        Box(
            Modifier
                .offset { IntOffset(((minutes - windowMinutes.value) * pxPerMinute).roundToInt(), 0) }
                .width(2.dp)
                .fillMaxHeight()
                .background(LumenColors.Accent.copy(alpha = 0.9f)),
        )
        Box(
            Modifier
                .offset { IntOffset(((minutes - windowMinutes.value) * pxPerMinute).roundToInt() - 3.dp.roundToPx(), 0) }
                .size(8.dp)
                .clip(CircleShape)
                .background(LumenColors.Accent),
        )
    }
}

@Composable
private fun GuideRow(
    channel: GuideChannel,
    slots: List<Slot>?,
    focusedSlot: Slot?,
    highlighted: Boolean,
    gridStart: Long,
    windowStart: Long,
    span: Long,
    windowMinutes: State<Float>,
    now: Long,
) {
    Row(Modifier.fillMaxWidth().height(RowHeight)) {
        ChannelCell(channel, highlighted, Modifier.width(ChannelColumn).fillMaxHeight())
        Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
            // null: still loading. Only cells near the window are composed.
            slots?.forEach { slot ->
                if (slot.stop > windowStart - span && slot.start < windowStart + 2 * span) {
                    key(slot.start) {
                        ProgrammeCell(slot, focused = slot == focusedSlot, gridStart, windowMinutes, now)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelCell(channel: GuideChannel, highlighted: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Logo(channel, Modifier.size(width = 72.dp, height = 48.dp))
        Text(
            channel.name,
            style = MaterialTheme.typography.titleSmall,
            color = if (highlighted) LumenColors.TextPrimary else LumenColors.TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The channel's logo fitted into a rounded tile, or its monogram while loading or if there's none. */
@Composable
private fun Logo(channel: GuideChannel, modifier: Modifier = Modifier) {
    var loaded by remember(channel.logoUrl) { mutableStateOf(false) }
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(LumenColors.Surface), contentAlignment = Alignment.Center) {
        if (!loaded) Monogram(channel.name, size = 40.dp)
        if (!channel.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ProgrammeCell(slot: Slot, focused: Boolean, gridStart: Long, windowMinutes: State<Float>, now: Long) {
    val density = LocalDensity.current
    val pxPerMinute = with(density) { PerMinute.toPx() }
    val startMinutes = (slot.start - gridStart) / MINUTE.toFloat()
    val lengthMinutes = (slot.stop - slot.start) / MINUTE.toFloat()
    val past = slot.stop <= now
    val live = now in slot.start until slot.stop

    val container by animateColorAsState(
        when {
            focused -> LumenColors.TextPrimary
            slot.programme == null -> LumenColors.Surface.copy(alpha = 0.45f)
            live -> LumenColors.SurfaceFocused
            else -> LumenColors.Surface
        },
        LumenMotion.focus(),
        label = "cell",
    )
    val titleColor = when {
        focused -> LumenColors.Background
        past || slot.programme == null -> LumenColors.TextTertiary
        else -> LumenColors.TextPrimary
    }
    val timeColor = if (focused) LumenColors.Background.copy(alpha = 0.7f) else LumenColors.TextTertiary

    Box(
        Modifier
            .offset { IntOffset(((startMinutes - windowMinutes.value) * pxPerMinute).roundToInt(), 0) }
            .width(PerMinute * lengthMinutes)
            .fillMaxHeight()
            .padding(end = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(container),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(
            Modifier
                // Keeps the title in view when the programme started before the window's left edge.
                .offset {
                    val hidden = ((windowMinutes.value - startMinutes) * pxPerMinute).roundToInt()
                    val room = (lengthMinutes * pxPerMinute).roundToInt() - 64.dp.roundToPx()
                    IntOffset(hidden.coerceIn(0, room.coerceAtLeast(0)), 0)
                }
                .padding(horizontal = 12.dp),
        ) {
            Text(
                slot.programme?.title ?: "No guide info",
                style = MaterialTheme.typography.titleSmall,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            slot.programme?.let {
                Text(formatTime(it.start), style = MaterialTheme.typography.labelSmall, color = timeColor, maxLines = 1)
            }
        }
        if (live && !focused && slot.programme != null) {
            // How far through the programme we are, as a hairline along the bottom.
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(((now - slot.start).toFloat() / (slot.stop - slot.start)).coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(LumenColors.Accent.copy(alpha = 0.6f)),
            )
        }
    }
}

/** Favourites and groups, in a panel over the left of the guide. */
@Composable
private fun GroupPicker(
    options: List<GuideOption>,
    selected: GuideList?,
    onPick: (GuideList) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val current = remember { FocusRequester() }
    val start = remember(options) { options.indexOfFirst { it.list == selected }.coerceAtLeast(0) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (start - 3).coerceAtLeast(0))
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))) {
        Column(
            Modifier
                .width(440.dp)
                .fillMaxHeight()
                .background(LumenColors.Surface)
                .padding(horizontal = 24.dp, vertical = SafeArea.Vertical),
        ) {
            Text("Show", style = MaterialTheme.typography.headlineSmall, color = LumenColors.TextPrimary)
            Spacer(Modifier.height(12.dp))
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(options, key = { _, o -> o.list.toString() }) { index, option ->
                    LumenListItem(
                        onClick = { onPick(option.list) },
                        selected = option.list == selected,
                        modifier = if (index == start) Modifier.focusRequester(current) else Modifier,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(option.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            option.subtitle?.let { Text(it, style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { current.requestFocus() } }
}

@Composable
private fun Message(title: String, body: String) {
    Column(Modifier.padding(top = 48.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = LumenColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = LumenColors.TextSecondary)
    }
}

/** "Tomorrow", "Thursday"…, or null for today. */
private fun dayName(time: Long, now: Long): String? {
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
private fun duration(millis: Long): String {
    val minutes = (millis / MINUTE).toInt()
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h h"
        else -> "$h h $m min"
    }
}
