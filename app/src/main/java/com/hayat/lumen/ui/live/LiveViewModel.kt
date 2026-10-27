package com.hayat.lumen.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hayat.lumen.data.ImportException
import com.hayat.lumen.data.LiveRepository
import com.hayat.lumen.data.db.ChannelRow
import com.hayat.lumen.data.db.GroupRow
import com.hayat.lumen.data.epg.EpgRepository
import com.hayat.lumen.data.epg.NowNext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GroupKey(val sourceId: Long, val name: String)

sealed interface RefreshState {
    data object Idle : RefreshState
    data class Running(val channels: Int) : RefreshState
    data class Failed(val message: String) : RefreshState
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class LiveViewModel(private val repo: LiveRepository, private val epg: EpgRepository) : ViewModel() {

    val groups: StateFlow<List<GroupRow>> =
        repo.groups.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** True when there are categories but all of them (or their playlists) are hidden. */
    val everythingHidden: StateFlow<Boolean> = combine(repo.groups, repo.categories) { shown, all ->
        shown.isEmpty() && all.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val picked = MutableStateFlow<GroupKey?>(null)

    /** The group whose channels are shown: the one picked, or the first if that one vanished. */
    val selected: StateFlow<GroupKey?> = combine(groups, picked) { groups, picked ->
        val keys = groups.map { GroupKey(it.sourceId, it.name) }
        picked?.takeIf { it in keys } ?: keys.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // flatMapLatest: when the selected group changes, drop the old query and start the new one.
    val channels: StateFlow<List<ChannelRow>> = selected
        .flatMapLatest { key -> if (key == null) flowOf(emptyList()) else repo.channels(key.sourceId, key.name) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Now/next for the shown group, from the stored guide. */
    val guide: StateFlow<Map<Long, NowNext>> = selected
        .flatMapLatest { key -> if (key == null) flowOf(emptyMap()) else epg.nowNextForGroup(key.sourceId, key.name) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val focusedChannel = MutableStateFlow<Long?>(null)

    /**
     * Now/next for the focused channel, which can also come from the provider's per-channel
     * guide when the stored one doesn't cover it. Debounced, so scrolling fast doesn't fire requests.
     */
    val focusedGuide: StateFlow<Pair<Long, NowNext>?> = focusedChannel
        .debounce(400)
        .flatMapLatest { id -> if (id == null) flowOf(null) else epg.nowNext(id).map { nn -> nn?.let { id to it } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun focusChannel(id: Long?) {
        focusedChannel.value = id
    }

    private val _refresh = MutableStateFlow<RefreshState>(RefreshState.Idle)
    val refresh: StateFlow<RefreshState> = _refresh

    fun select(group: GroupRow) {
        picked.value = GroupKey(group.sourceId, group.name)
    }

    private val _notice = MutableStateFlow<String?>(null)
    /** A short confirmation (e.g. after hiding a category), cleared after a few seconds. */
    val notice: StateFlow<String?> = _notice
    private var noticeJob: Job? = null

    /** Hides [group] from Live. The group below it (or above, at the end) becomes the selected one. */
    fun hide(group: GroupRow) {
        val list = groups.value
        val index = list.indexOfFirst { it.sourceId == group.sourceId && it.name == group.name }
        val neighbour = list.getOrNull(index + 1) ?: list.getOrNull(index - 1)
        picked.value = neighbour?.let { GroupKey(it.sourceId, it.name) }
        viewModelScope.launch { repo.hideGroups(group.sourceId, listOf(group.name)) }
        noticeJob?.cancel()
        noticeJob = viewModelScope.launch {
            _notice.value = "Hid “${group.name}”. Show it again in Categories."
            delay(4_000)
            _notice.value = null
        }
    }

    fun toggleFavourite(channelId: Long) {
        viewModelScope.launch { repo.toggleFavourite(channelId) }
    }

    fun refreshAll() {
        if (_refresh.value is RefreshState.Running) return
        viewModelScope.launch {
            _refresh.value = RefreshState.Running(0)
            try {
                for (source in repo.sources.first().filterNot { it.hidden }) {
                    repo.refresh(source.id) { _refresh.value = RefreshState.Running(it) }
                    epg.updateInBackground(source.id)
                }
                _refresh.value = RefreshState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImportException) {
                _refresh.value = RefreshState.Failed(e.message ?: "Refresh failed.")
            } catch (e: Exception) {
                _refresh.value = RefreshState.Failed("Refresh failed.")
            }
        }
    }
}
