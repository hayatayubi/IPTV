package com.hayat.lumen.ui.guide

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hayat.lumen.data.LiveRepository
import com.hayat.lumen.data.db.GroupRow
import com.hayat.lumen.data.epg.EpgRepository
import com.hayat.lumen.ui.live.GroupKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the guide lists: the favourites, or one group of a playlist. */
sealed interface GuideList {
    data object Favourites : GuideList
    data class Group(val key: GroupKey) : GuideList
}

data class GuideOption(val list: GuideList, val title: String, val subtitle: String?)

data class GuideChannel(val id: Long, val name: String, val number: Int?, val logoUrl: String?)

@OptIn(ExperimentalCoroutinesApi::class)
class GuideViewModel(private val repo: LiveRepository, private val epg: EpgRepository) : ViewModel() {

    /** The grid runs from the last half hour to a day ahead (the stored guide reaches 36 h). */
    val gridStart: Long = GuideSlots.floorHalfHour(System.currentTimeMillis()) - GuideSlots.HALF_HOUR
    val gridEnd: Long = gridStart + 25 * 60 * 60_000L

    private val favourites = repo.favourites
    private val groups = repo.groups

    /** Favourites first (when there are any), then every group, as in Live. */
    val options: StateFlow<List<GuideOption>> = combine(favourites, groups) { favs, groups ->
        val multipleSources = groups.distinctBy { it.sourceId }.size > 1
        buildList {
            if (favs.isNotEmpty()) add(GuideOption(GuideList.Favourites, "Favourites", null))
            groups.forEach { add(it.toOption(multipleSources)) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val picked = MutableStateFlow<GuideList?>(null)

    /** The picked list, or the first option if nothing was picked (or it vanished). */
    val selected: StateFlow<GuideOption?> = combine(options, picked) { options, picked ->
        options.firstOrNull { it.list == picked } ?: options.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val channels: StateFlow<List<GuideChannel>> = selected
        .map { it?.list }
        .distinctUntilChanged()
        .flatMapLatest { list ->
            when (list) {
                null -> flowOf(emptyList())
                GuideList.Favourites -> favourites.map { rows -> rows.map { GuideChannel(it.id, it.name, it.number, it.logoUrl) } }
                is GuideList.Group -> repo.channels(list.key.sourceId, list.key.name)
                    .map { rows -> rows.map { GuideChannel(it.id, it.name, it.number, it.logoUrl) } }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val visibleRows = MutableStateFlow(0..0)

    /**
     * Schedules for the rows on screen and a margin around them, as slots. Loaded in blocks of
     * [BLOCK] rows, so scrolling down a long group doesn't re-query on every row.
     */
    val slots: StateFlow<Map<Long, List<Slot>>> = combine(channels, visibleRows) { channels, rows ->
        if (channels.isEmpty()) return@combine emptyList()
        val first = ((rows.first - MARGIN).coerceAtLeast(0) / BLOCK) * BLOCK
        val last = ((rows.last + MARGIN) / BLOCK + 1) * BLOCK
        channels.subList(first.coerceAtMost(channels.size), last.coerceAtMost(channels.size)).map { it.id }
    }
        .distinctUntilChanged()
        .flatMapLatest { ids ->
            epg.schedule(ids, gridStart, gridEnd).map { byChannel ->
                // Every loaded channel gets slots, even with no guide: missing from the map means "still loading".
                ids.associateWith { GuideSlots.build(byChannel[it].orEmpty(), gridStart, gridEnd) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // Where the highlight is. Kept here (not in the screen) so coming back from the player
    // lands on the same programme. Compose state in a ViewModel is fine: the screen reads it directly.

    /** The focused row. */
    var row by mutableIntStateOf(0)

    /** The time the highlight follows when moving Up/Down, like a cursor's column. */
    var anchor by mutableLongStateOf(System.currentTimeMillis())

    /** Start of the time window on screen. */
    var windowStart by mutableLongStateOf(GuideSlots.floorHalfHour(System.currentTimeMillis()))

    fun pick(list: GuideList) {
        if (list == selected.value?.list) return
        picked.value = list
        row = 0
    }

    fun setVisibleRows(rows: IntRange) {
        visibleRows.value = rows
    }

    private fun GroupRow.toOption(multipleSources: Boolean) =
        GuideOption(GuideList.Group(GroupKey(sourceId, name)), label, sourceName.takeIf { multipleSources })

    private companion object {
        const val BLOCK = 20
        const val MARGIN = 10
    }
}
