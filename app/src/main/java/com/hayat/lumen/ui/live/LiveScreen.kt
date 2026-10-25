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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hayat.lumen.LocalAppContainer
import com.hayat.lumen.data.db.ChannelRow
import com.hayat.lumen.ui.components.LumenButton
import com.hayat.lumen.ui.components.LumenListItem
import com.hayat.lumen.ui.components.Monogram
import com.hayat.lumen.ui.theme.LumenColors
import com.hayat.lumen.ui.theme.SafeArea
import java.text.NumberFormat

/**
 * Live: groups on the left, the selected group's channels on the right.
 * Moving focus onto a group shows its channels (no OK needed); OK on a channel plays it.
 */
@Composable
fun LiveScreen(onPlay: (channelId: Long) -> Unit) {
    val container = LocalAppContainer.current
    val vm = viewModel { LiveViewModel(container.live) }
    val groups by vm.groups.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val channels by vm.channels.collectAsStateWithLifecycle()
    val refresh by vm.refresh.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    val multipleSources = remember(groups) { groups.distinctBy { it.sourceId }.size > 1 }
    val groupFocus = remember { FocusRequester() }
    val channelFocus = remember { FocusRequester() }
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
                        RefreshState.Idle -> ""
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
            }
            Spacer(Modifier.height(16.dp))
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
                        onClick = {
                            container.lastChannelId.value = channel.id
                            onPlay(channel.id)
                        },
                        modifier = if (channel.id == returnTo) Modifier.focusRequester(channelFocus) else Modifier,
                    )
                }
            }
        }
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
private fun ChannelItem(channel: ChannelRow, position: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LumenListItem(onClick = onClick, modifier = modifier, minHeight = 64.dp) {
        Text(
            (channel.number ?: position).toString(),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(48.dp),
        )
        Monogram(channel.name, size = 44.dp)
        Text(
            channel.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatCount(n: Int): String = NumberFormat.getIntegerInstance().format(n)
