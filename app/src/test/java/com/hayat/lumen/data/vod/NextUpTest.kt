package com.hayat.lumen.data.vod

import com.hayat.lumen.data.db.VodKind
import com.hayat.lumen.data.db.WatchProgressEntity
import com.hayat.lumen.data.xtream.XtreamEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextUpTest {
    private val episodes = (1..3).map { XtreamEpisode("e$it", 1, it, "Episode $it", "mkv", 2400, null, null) }

    private fun progress(id: String, positionMs: Long, finished: Boolean, at: Long) = WatchProgressEntity(
        sourceId = 1, kind = VodKind.SERIES, itemId = id, seriesId = "s", season = 1, episode = null,
        positionMs = positionMs, durationMs = 2_400_000, finished = finished, updatedAt = at,
    )

    @Test
    fun nothingWatchedStartsAtTheFirstEpisode() {
        assertEquals(NextUp.Pick(episodes[0], null), NextUp.pick(episodes, emptyList()))
    }

    @Test
    fun resumesTheEpisodeYouWereWatching() {
        val pick = NextUp.pick(episodes, listOf(progress("e1", 2_400_000, true, 1), progress("e2", 600_000, false, 2)))
        assertEquals(NextUp.Pick(episodes[1], 600_000), pick)
    }

    @Test
    fun finishedEpisodeMovesOnToTheNext() {
        val pick = NextUp.pick(episodes, listOf(progress("e2", 2_390_000, true, 5), progress("e3", 100_000, false, 1)))
        assertEquals(NextUp.Pick(episodes[2], 100_000), pick)
    }

    @Test
    fun finishedLastEpisodeStartsOver() {
        assertEquals(NextUp.Pick(episodes[0], null), NextUp.pick(episodes, listOf(progress("e3", 2_390_000, true, 1))))
    }

    @Test
    fun afterGivesTheNextEpisodeOrNull() {
        assertEquals(episodes[1], NextUp.after(episodes, "e1"))
        assertNull(NextUp.after(episodes, "e3"))
        assertNull(NextUp.after(episodes, "missing"))
    }
}
