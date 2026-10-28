package com.hayat.lumen.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hayat.lumen.LocalAppContainer
import com.hayat.lumen.data.db.ChannelRow
import com.hayat.lumen.data.epg.NowNext
import com.hayat.lumen.ui.components.LumenButton
import com.hayat.lumen.ui.components.LumenListItem
import com.hayat.lumen.ui.components.ChannelLogo
import com.hayat.lumen.ui.components.QualityTag
import com.hayat.lumen.ui.components.ProgrammeProgress
import com.hayat.lumen.ui.components.formatTime
import com.hayat.lumen.ui.theme.LumenColors
import com.hayat.lumen.util.ChannelNames
import com.hayat.lumen.ui.theme.SafeArea
import java.text.NumberFormat

/**
 * Live: groups on the left, the selected group's channels on the right.
 * Moving focus onto a group shows its channels (no OK needed); holding OK on a group hides it.
 * OK on a channel plays it, holding OK adds it to (or removes it from) favourites.
 */
@Composable
fun LiveScreen(onPlay: (channelId: Long) -> Unit, onOpenGuide: () -> Unit, onManageCategories: () -> Unit) {
    val container = LocalAppContainer.current
    val vm = viewModel { LiveViewModel(container.live, container.epg, container.vod) }
    val groups by vm.groups.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val channels by vm.channels.collectAsStateWithLifecycle()
    val refresh by vm.refresh.collectAsStateWithLifecycle()
    val guide by vm.guide.collectAsStateWithLifecycle()
    val focusedGuide by vm.focusedGuide.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val everythingHidden by vm.everythingHidden.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    val multipleSources = remember(groups) { groups.distinctBy { it.sourceId }.size > 1 }
    val groupFocus = remember { FocusRequester() }
    val channelFocus = remember { FocusRequester() }
    val categoriesFocus = remember { FocusRequester() }
    // Set when a group is hidden: its row (which had focus) disappears, so focus moves to the new selection.
    var refocusGroup by remember { mutableStateOf(false) }
    val channelListState = rememberLazyListState()

    // Coming back from the player: put focus on the channel that was playing.
    // Read once when this screen (re)enters composition.
    val returnTo = remember { container.lastChannelId.value }
    var initialFocusDone by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxSize()
            .background(LumenColors.Background)
            .padding(horizontal = SafeArea.Horizontal, vertical = SafeArea.Vertical),
    ) {
        // Groups column
        Column(Modifier.width(340.dp).fillMaxHeight()) {
            Text("Live", style = MaterialTheme.typography.headlineLarge, color = LumenColors.TextPrimary)
            Spacer(Modifier.height(16.dp))
            LazyColumn(
                // focusRestorer: re-entering this list lands on the last focused group.
                modifier = Modifier.focusRestorer(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(groups, key = { _, g -> "${g.sourceId}/${g.name}" }) { index, group ->
                    if (multipleSources && (index == 0 || groups[index - 1].sourceId != group.sourceId)) {
                        Text(
                            group.sourceName.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = LumenColors.TextTertiary,
                            modifier = Modifier.padding(start = 16.dp, top = if (index == 0) 0.dp else 16.dp, bottom = 4.dp),
                        )
                    }
                    val isSelected = selected?.sourceId == group.sourceId && selected?.name == group.name
                    LumenListItem(
                        onClick = { focusManager.moveFocus(FocusDirection.Right) },
                        onLongClick = {
                            refocusGroup = true
                            vm.hide(group)
                        },
                        selected = isSelected,
                        modifier = Modifier
                            .then(if (isSelected) Modifier.focusRequester(groupFocus) else Modifier)
                            .onFocusChanged { if (it.isFocused) vm.select(group) },
                    ) {
                        Text(
                            group.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(formatCount(group.channelCount), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        Spacer(Modifier.width(32.dp))

        // Channels column
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selected?.name.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = LumenColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    when (val r = refresh) {
                        is RefreshState.Failed -> r.message
                        is RefreshState.Running -> if (r.channels == 0) "Refreshing…" else "Refreshing… ${formatCount(r.channels)}"
                        RefreshState.Idle -> notice.orEmpty()
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = LumenColors.TextSecondary,
                    modifier = Modifier.padding(end = 16.dp),
                )
                LumenButton(
                    text = "Refresh",
                    onClick = vm::refreshAll,
                    enabled = refresh !is RefreshState.Running,
                )
                Spacer(Modifier.width(12.dp))
                LumenButton(text = "TV guide", onClick = onOpenGuide)
                Spacer(Modifier.width(12.dp))
                LumenButton(text = "Categories", onClick = onManageCategories, modifier = Modifier.focusRequester(categoriesFocus))
            }
            Spacer(Modifier.height(16.dp))
            if (everythingHidden) {
                Text(
                    "All categories are hidden. Open Categories to choose what shows here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = LumenColors.TextSecondary,
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyColumn(
                state = channelListState,
                modifier = Modifier.focusRestorer(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(channels, key = { _, c -> c.id }) { index, channel ->
                    ChannelItem(
                        channel = channel,
                        position = index + 1,
                        nowNext = guide[channel.id] ?: focusedGuide?.takeIf { it.first == channel.id }?.second,
                        onClick = {
                            container.lastChannelId.value = channel.id
                            onPlay(channel.id)
                        },
                        onLongClick = { vm.toggleFavourite(channel.id) },
                        modifier = (if (channel.id == returnTo) Modifier.focusRequester(channelFocus) else Modifier)
                            .onFocusChanged { if (it.isFocused) vm.focusChannel(channel.id) },
                    )
                }
            }
        }
    }

    LaunchedEffect(groups, everythingHidden) {
        if (!refocusGroup) return@LaunchedEffect
        withFrameNanos { } // let the new selection's row compose first
        if (runCatching { groupFocus.requestFocus() }.isSuccess) {
            refocusGroup = false
        } else if (everythingHidden) {
            refocusGroup = false
            runCatching { categoriesFocus.requestFocus() }
        }
    }

    // Nothing to show (everything hidden): start on Categories.
    LaunchedEffect(everythingHidden) {
        if (everythingHidden && !initialFocusDone) initialFocusDone = runCatching { categoriesFocus.requestFocus() }.isSuccess
    }

    // First focus: the channel we came back from, else the selected group.
    LaunchedEffect(groups, channels) {
        if (initialFocusDone || groups.isEmpty()) return@LaunchedEffect
        val index = channels.indexOfFirst { it.id == returnTo }
        if (returnTo != null && index < 0 && channels.isEmpty()) return@LaunchedEffect // channels still loading
        if (index >= 0) {
            channelListState.scrollToItem(index)
            withFrameNanos { } // let the scrolled-to row compose before focusing it
            initialFocusDone = runCatching { channelFocus.requestFocus() }.isSuccess
        }
        if (!initialFocusDone) initialFocusDone = runCatching { groupFocus.requestFocus() }.isSuccess
    }
}

@Composable
private fun ChannelItem(
    channel: ChannelRow,
    position: Int,
    nowNext: NowNext?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LumenListItem(onClick = onClick, onLongClick = onLongClick, modifier = modifier, minHeight = 64.dp) {
        Text(
            (channel.number ?: position).toString(),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(48.dp),
        )
        ChannelLogo(channel.name, channel.logoUrl, size = 40.dp)
        // LocalContentColor flips to dark when the row is focused, so guide text follows it
        // (dimmed) instead of using a fixed grey that would vanish on the light focused row.
        val dim = LocalContentColor.current.copy(alpha = 0.7f)
        val name = remember(channel.name) { ChannelNames.display(channel.name) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    name.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                name.tags.forEach { QualityTag(it, color = LocalContentColor.current.copy(alpha = 0.7f)) }
            }
            nowNext?.now?.let { now ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        now.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = dim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    ProgrammeProgress(now.progress(System.currentTimeMillis()), color = dim, modifier = Modifier.width(64.dp))
                }
            }
        }
        nowNext?.next?.let { next ->
            Text(
                "${formatTime(next.start)}  ${next.title}",
                style = MaterialTheme.typography.bodySmall,
                color = dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 260.dp),
            )
        }
        if (channel.favourite) {
            Text("★", style = MaterialTheme.typography.titleMedium, color = LumenColors.Accent)
        }
    }
}

private fun formatCount(n: Int): String = NumberFormat.getIntegerInstance().format(n)
