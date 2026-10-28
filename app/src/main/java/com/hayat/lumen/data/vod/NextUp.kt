package com.hayat.lumen.data.vod

import com.hayat.lumen.data.db.WatchProgressEntity
import com.hayat.lumen.data.xtream.XtreamEpisode

/** Which episode a series' Play button starts, Apple TV style ("Resume S1 · E3", "Play S1 · E4"). */
object NextUp {
    data class Pick(val episode: XtreamEpisode, val resumeMs: Long?)

    /**
     * The episode you were last watching, if unfinished; else the one after it; else (nothing
     * watched, or the last episode finished) the first. [episodes] is in watching order.
     */
    fun pick(episodes: List<XtreamEpisode>, progress: List<WatchProgressEntity>): Pick? {
        if (episodes.isEmpty()) return null
        val byId = progress.associateBy { it.itemId }
        val last = progress.filter { p -> episodes.any { it.id == p.itemId } }.maxByOrNull { it.updatedAt }
            ?: return Pick(episodes.first(), null)
        val index = episodes.indexOfFirst { it.id == last.itemId }
        if (!last.finished) return Pick(episodes[index], VodRepository.resumePosition(last))
        val next = episodes.getOrNull(index + 1) ?: return Pick(episodes.first(), null)
        return Pick(next, VodRepository.resumePosition(byId[next.id]))
    }

    fun after(episodes: List<XtreamEpisode>, episodeId: String): XtreamEpisode? =
        episodes.getOrNull(episodes.indexOfFirst { it.id == episodeId }.takeIf { it >= 0 }?.plus(1) ?: return null)
}
