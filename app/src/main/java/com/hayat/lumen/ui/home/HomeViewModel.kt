package com.hayat.lumen.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hayat.lumen.data.LiveRepository
import com.hayat.lumen.data.db.FavouriteRow
import com.hayat.lumen.data.epg.EpgRepository
import com.hayat.lumen.data.epg.NowNext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val repo: LiveRepository, epg: EpgRepository) : ViewModel() {

    /** null until the first query returns, so Home doesn't flash its empty state. */
    val favourites: StateFlow<List<FavouriteRow>?> =
        repo.favourites.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** What's on each favourite now, by channel id. Re-queried only when the set of channels changes. */
    val nowNext: StateFlow<Map<Long, NowNext>> = favourites
        .filterNotNull()
        .map { list -> list.map { it.id } }
        .distinctUntilChanged()
        .flatMapLatest { ids -> epg.nowNextForChannels(ids) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun remove(channel: FavouriteRow) {
        viewModelScope.launch { repo.removeFavourite(channel.sourceId, channel.stableKey) }
    }

    suspend fun streamUrl(channelId: Long): String? = repo.channel(channelId)?.streamUrl
}
