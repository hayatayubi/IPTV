package com.hayat.lumen.ui.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hayat.lumen.data.ImportException
import com.hayat.lumen.data.LiveRepository
import com.hayat.lumen.data.db.ChannelRow
import com.hayat.lumen.data.db.GroupRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GroupKey(val sourceId: Long, val name: String)

sealed interface RefreshState {
    data object Idle : RefreshState
    data class Running(val channels: Int) : RefreshState
    data class Failed(val message: String) : RefreshState
}

@OptIn(ExperimentalCoroutinesApi::class)
class LiveViewModel(private val repo: LiveRepository) : ViewModel() {

    val groups: StateFlow<List<GroupRow>> =
        repo.groups.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    private val _refresh = MutableStateFlow<RefreshState>(RefreshState.Idle)
    val refresh: StateFlow<RefreshState> = _refresh

    fun select(group: GroupRow) {
        picked.value = GroupKey(group.sourceId, group.name)
    }

    fun refreshAll() {
        if (_refresh.value is RefreshState.Running) return
        viewModelScope.launch {
            _refresh.value = RefreshState.Running(0)
            try {
                for (source in repo.sources.first()) {
                    repo.refresh(source.id) { _refresh.value = RefreshState.Running(it) }
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
