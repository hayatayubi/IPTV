package com.hayat.lumen.ui.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.hayat.lumen.LocalAppContainer
import com.hayat.lumen.data.ImportException
import com.hayat.lumen.data.LiveRepository
import com.hayat.lumen.data.db.SourceEntity
import com.hayat.lumen.data.db.SourceType
import com.hayat.lumen.data.epg.EpgRepository
import com.hayat.lumen.data.vod.VodRepository
import com.hayat.lumen.data.xtream.XtreamLogin
import com.hayat.lumen.ui.components.LumenButton
import com.hayat.lumen.ui.components.LumenListItem
import com.hayat.lumen.ui.components.LumenTextField
import com.hayat.lumen.ui.theme.LumenColors
import com.hayat.lumen.util.UrlRedaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.NumberFormat

/** A playlist row in Settings. [busy] is the import progress (channels so far) while refreshing. */
data class PlaylistUi(
    val source: SourceEntity,
    val channels: Int,
    val busy: Int?,
    val error: String?,
)

class PlaylistsViewModel(
    private val live: LiveRepository,
    private val epg: EpgRepository,
    private val vod: VodRepository,
) : ViewModel() {
    private val progress = MutableStateFlow<Map<Long, Int>>(emptyMap())
    private val errors = MutableStateFlow<Map<Long, String>>(emptyMap())

    val playlists: StateFlow<List<PlaylistUi>> =
        combine(live.sources, live.categories, progress, errors) { sources, categories, progress, errors ->
            val counts = categories.groupBy { it.sourceId }.mapValues { (_, rows) -> rows.sumOf { it.channelCount } }
            sources.map { PlaylistUi(it, counts[it.id] ?: 0, progress[it.id], errors[it.id]) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refresh(source: SourceEntity) = run(source.id) { onProgress -> live.refresh(source.id, onProgress) }

    fun rename(source: SourceEntity, name: String) {
        viewModelScope.launch { live.renameSource(source.id, name) }
    }

    fun setHidden(source: SourceEntity, hidden: Boolean) {
        viewModelScope.launch {
            live.setSourceHidden(source.id, hidden)
            if (!hidden) epg.updateInBackground(source.id) // hidden playlists skip guide updates
        }
    }

    /** Removes the playlist and everything kept for it. [onRemoved] gets true when it was the last one. */
    fun remove(source: SourceEntity, onRemoved: (lastOne: Boolean) -> Unit) {
        viewModelScope.launch {
            live.remove(source.id)
            onRemoved(!live.hasSources())
        }
    }

    /** New link or login; [onDone] runs once it imported. On failure the old one stays and the error shows. */
    fun changeConnection(source: SourceEntity, url: String?, login: XtreamLogin?, onDone: () -> Unit) =
        run(source.id, onDone) { onProgress -> live.changeConnection(source.id, url, login, onProgress) }

    private fun run(sourceId: Long, onDone: () -> Unit = {}, work: suspend (onProgress: (Int) -> Unit) -> Unit) {
        if (sourceId in progress.value) return
        viewModelScope.launch {
            progress.update { it + (sourceId to 0) }
            errors.update { it - sourceId }
            try {
                work { n -> progress.update { it + (sourceId to n) } }
                epg.updateInBackground(sourceId)
                vod.refreshInBackground(sourceId)
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImportException) {
                errors.update { it + (sourceId to (e.message ?: "Refresh failed.")) }
            } catch (e: Exception) {
                errors.update { it + (sourceId to "Refresh failed.") }
            } finally {
                progress.update { it - sourceId }
            }
        }
    }
}

private sealed interface PlaylistDialog {
    val source: SourceEntity
    data class Menu(override val source: SourceEntity) : PlaylistDialog
    data class Rename(override val source: SourceEntity) : PlaylistDialog
    data class Connection(override val source: SourceEntity) : PlaylistDialog
    data class ConfirmRemove(override val source: SourceEntity) : PlaylistDialog
}

/** Settings → Playlists: each playlist with what you can do to it, and Add playlist. */
@Composable
fun PlaylistsSection(onAddPlaylist: () -> Unit, onNoPlaylists: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val vm = viewModel { PlaylistsViewModel(container.live, container.epg, container.vod) }
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<PlaylistDialog?>(null) }

    Column(modifier) {
        Text(
            "OK on a playlist to refresh, rename, change its link or login, hide or remove it.",
            style = MaterialTheme.typography.bodyLarge,
            color = LumenColors.TextSecondary,
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            modifier = Modifier.focusRestorer(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(playlists, key = { it.source.id }) { item ->
                PlaylistRow(item, onClick = { dialog = PlaylistDialog.Menu(item.source) })
            }
            item(key = "add") {
                Row(Modifier.padding(top = 16.dp)) {
                    LumenButton(text = "Add playlist", onClick = onAddPlaylist)
                }
            }
        }
    }

    // Dialogs read the latest copy of the playlist, so a rename shows straight away.
    val current = dialog?.let { d -> playlists.firstOrNull { it.source.id == d.source.id } }
    val close = { dialog = null }
    when (val d = dialog) {
        null -> Unit
        is PlaylistDialog.Menu -> ActionsDialog(
            title = current?.source?.name ?: d.source.name,
            message = current?.let(::status),
            actions = listOf(
                "Refresh now" to { vm.refresh(d.source); close() },
                "Rename" to { dialog = PlaylistDialog.Rename(d.source) },
                (if (d.source.type == SourceType.XTREAM) "Change login" else "Change link") to { dialog = PlaylistDialog.Connection(d.source) },
                (if (current?.source?.hidden == true) "Show playlist" else "Hide playlist") to {
                    vm.setHidden(d.source, !(current?.source?.hidden ?: d.source.hidden))
                    close()
                },
                "Remove playlist" to { dialog = PlaylistDialog.ConfirmRemove(d.source) },
            ),
            onDismiss = close,
        )
        is PlaylistDialog.Rename -> RenameDialog(
            title = "Rename playlist",
            current = d.source.name,
            original = null,
            onSave = { vm.rename(d.source, it); close() },
            onDismiss = close,
        )
        is PlaylistDialog.Connection -> ConnectionDialog(
            source = d.source,
            busy = current?.busy,
            error = current?.error,
            onSave = { url, login -> vm.changeConnection(d.source, url, login, onDone = close) },
            onDismiss = close,
        )
        is PlaylistDialog.ConfirmRemove -> ActionsDialog(
            title = "Remove “${d.source.name}”?",
            message = "Its channels, favourites, guide, hidden and renamed categories, My List and watch history go too.",
            actions = listOf(
                "Keep it" to close,
                "Remove" to {
                    close()
                    vm.remove(d.source) { lastOne -> if (lastOne) onNoPlaylists() }
                },
            ),
            onDismiss = close,
        )
    }
}

@Composable
private fun PlaylistRow(item: PlaylistUi, onClick: () -> Unit) {
    LumenListItem(onClick = onClick, minHeight = 72.dp) {
        Column(Modifier.weight(1f)) {
            Text(item.source.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                status(item),
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.source.hidden) Text("Hidden", style = MaterialTheme.typography.labelLarge)
    }
}

private fun status(item: PlaylistUi): String {
    val count = NumberFormat.getIntegerInstance()
    val type = if (item.source.type == SourceType.XTREAM) "Xtream" else "M3U"
    val parts = mutableListOf(type, UrlRedaction.hostOnly(item.source.url))
    when {
        item.busy != null -> parts += if (item.busy == 0) "Refreshing…" else "Refreshing… ${count.format(item.busy)} channels"
        else -> {
            parts += "${count.format(item.channels)} channels"
            item.source.lastRefreshed?.let {
                parts += "refreshed ${DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)}"
            }
        }
    }
    return parts.joinToString(" · ") + (item.error?.let { "\n$it" } ?: "")
}

/**
 * Change an M3U link or an Xtream login. The old link isn't shown (it may hold a password);
 * for Xtream the server and username are, and a blank password keeps the current one.
 */
@Composable
private fun ConnectionDialog(
    source: SourceEntity,
    busy: Int?,
    error: String?,
    onSave: (url: String?, login: XtreamLogin?) -> Unit,
    onDismiss: () -> Unit,
) {
    val xtream = source.type == SourceType.XTREAM
    var url by remember { mutableStateOf("") }
    var server by remember { mutableStateOf(if (xtream) source.url else "") }
    var username by remember { mutableStateOf(source.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    val count = NumberFormat.getIntegerInstance()

    SettingsDialog(
        title = if (xtream) "Change login" else "Change link",
        onDismiss = onDismiss,
        width = 640.dp,
        message = if (xtream) "Leave the password empty to keep the current one."
        else "Now: ${UrlRedaction.hostOnly(source.url)}. Paste the new M3U or Xtream (get.php) link.",
    ) {
        if (xtream) {
            LumenTextField(value = server, onValueChange = { server = it }, label = "Server", keyboardType = KeyboardType.Uri, modifier = Modifier.focusRequester(first))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LumenTextField(value = username, onValueChange = { username = it }, label = "Username", modifier = Modifier.weight(1f))
                LumenTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    placeholder = "Unchanged",
                    keyboardType = KeyboardType.Password,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            LumenTextField(
                value = url,
                onValueChange = { url = it },
                label = "Playlist link",
                placeholder = "http://…",
                keyboardType = KeyboardType.Uri,
                modifier = Modifier.focusRequester(first),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LumenButton(
                text = if (busy != null) "Checking…" else "Save and refresh",
                enabled = busy == null,
                onClick = {
                    problem = null
                    if (xtream) {
                        val login = XtreamLogin.of(server, username, password.ifBlank { source.password.orEmpty() })
                        if (login == null) problem = "Enter the server address, username and password." else onSave(null, login)
                    } else {
                        if (url.isBlank()) problem = "Paste a playlist link first." else onSave(url, null)
                    }
                },
            )
            LumenButton(text = "Cancel", onClick = onDismiss)
        }
        val line = when {
            busy != null -> if (busy == 0) "Downloading…" else "Importing… ${count.format(busy)} channels"
            problem != null -> problem
            error != null -> "$error The old ${if (xtream) "login" else "link"} is kept."
            else -> null
        }
        if (line != null) Text(line, style = MaterialTheme.typography.bodyMedium, color = LumenColors.TextPrimary)
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}
