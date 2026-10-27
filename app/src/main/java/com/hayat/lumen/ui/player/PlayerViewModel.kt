package com.hayat.lumen.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hayat.lumen.data.LiveRepository
import com.hayat.lumen.data.db.PlayableChannel
import com.hayat.lumen.data.epg.EpgRepository
import com.hayat.lumen.data.epg.NowNext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class Tuned(val channel: PlayableChannel, val position: Int, val total: Int)

/**
 * Which channel is tuned, and the list to zap through with Up/Down: the favourites
 * when [inFavourites] (opened from Home), else the channel's group.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel(
    private val repo: LiveRepository,
    epg: EpgRepository,
    private val lastChannelId: MutableStateFlow<Long?>,
    startChannelId: Long,
    inFavourites: Boolean,
) : ViewModel() {

    private var list: List<PlayableChannel> = emptyList()
    private val _tuned = MutableStateFlow<Tuned?>(null)
    val tuned: StateFlow<Tuned?> = _tuned

    /** What's on the tuned channel. */
    val nowNext: StateFlow<NowNext?> = _tuned
        .map { it?.channel?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else epg.nowNext(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            val start = repo.channel(startChannelId) ?: return@launch
            list = repo.playableFavourites().takeIf { inFavourites && it.any { c -> c.id == startChannelId } }
                ?: repo.playableInGroup(start.sourceId, start.groupName)
            val index = list.indexOfFirst { it.id == startChannelId }
            if (index >= 0) tune(index)
        }
    }

    /** Moves through the group, wrapping at either end. */
    fun step(delta: Int) {
        val current = _tuned.value ?: return
        if (list.isEmpty()) return
        tune(Math.floorMod(current.position - 1 + delta, list.size))
    }

    private fun tune(index: Int) {
        val channel = list[index]
        _tuned.value = Tuned(channel, position = index + 1, total = list.size)
        lastChannelId.value = channel.id
    }
}
