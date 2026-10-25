package com.hayat.lumen.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hayat.lumen.data.LiveRepository
import com.hayat.lumen.data.db.PlayableChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class Tuned(val channel: PlayableChannel, val position: Int, val total: Int)

/** Which channel is tuned, and the group list to zap through with Up/Down. */
class PlayerViewModel(
    private val repo: LiveRepository,
    private val lastChannelId: MutableStateFlow<Long?>,
    startChannelId: Long,
) : ViewModel() {

    private var list: List<PlayableChannel> = emptyList()
    private val _tuned = MutableStateFlow<Tuned?>(null)
    val tuned: StateFlow<Tuned?> = _tuned

    init {
        viewModelScope.launch {
            val start = repo.channel(startChannelId) ?: return@launch
            list = repo.playableInGroup(start.sourceId, start.groupName)
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
