package com.hayat.lumen.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hayat.lumen.data.LiveRepository
import com.hayat.lumen.data.db.PlayableChannel
import com.hayat.lumen.data.epg.EpgRepository
import com.hayat.lumen.data.epg.NowNext
import com.hayat.lumen.data.epg.Programme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class Tuned(val channel: PlayableChannel, val position: Int, val total: Int)

/** The channel whose guide the player's guide strip shows, and its neighbours in the zapping list. */
data class Browsed(
    val channel: PlayableChannel,
    val isTuned: Boolean,
    val previous: PlayableChannel?,
    val next: PlayableChannel?,
)

/**
 * Which channel is tuned, and the list to zap through with Up/Down: the favourites
 * when [inFavourites] (opened from Home), else the channel's group.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel(
    private val repo: LiveRepository,
    private val epg: EpgRepository,
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

    // The guide strip: which channel of [list] it shows, or null when it's closed. Browsing never
    // changes the stream; only [watchBrowsed] does.
    private val browsing = MutableStateFlow<Int?>(null)

    val browsed: StateFlow<Browsed?> = combine(browsing, _tuned) { index, tuned ->
        if (index == null || list.isEmpty()) return@combine null
        Browsed(
            channel = list[index],
            isTuned = tuned?.channel?.id == list[index].id,
            previous = list.getOrNull(Math.floorMod(index - 1, list.size)).takeIf { list.size > 1 },
            next = list.getOrNull(Math.floorMod(index + 1, list.size)).takeIf { list.size > 1 },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The browsed channel's programmes from now to a day ahead (null while loading). */
    val schedule: StateFlow<List<Programme>?> = browsed
        .map { it?.channel?.id }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(null)
            } else {
                val now = System.currentTimeMillis()
                epg.schedule(listOf(id), now, now + GUIDE_AHEAD_MS).map<_, List<Programme>?> { it[id].orEmpty() }.onStart { emit(null) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Opens the guide strip on the playing channel. */
    fun openGuide() {
        browsing.value = (_tuned.value ?: return).position - 1
    }

    fun closeGuide() {
        browsing.value = null
    }

    /** Shows the guide of the channel above (-1) or below (+1), wrapping like zapping. */
    fun browse(delta: Int) {
        val index = browsing.value ?: return
        if (list.isEmpty()) return
        browsing.value = Math.floorMod(index + delta, list.size)
    }

    /** Switches the stream to the browsed channel and closes the strip. */
    fun watchBrowsed() {
        val index = browsing.value ?: return
        browsing.value = null
        if (index != (_tuned.value?.position ?: 0) - 1) tune(index)
    }

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

    private companion object {
        const val GUIDE_AHEAD_MS = 24 * 60 * 60_000L
    }
}
