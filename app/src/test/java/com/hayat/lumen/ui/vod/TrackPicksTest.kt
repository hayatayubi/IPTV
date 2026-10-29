package com.hayat.lumen.ui.vod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackPicksTest {
    private val many = listOf("fra", "eng", "deu", "eng", "ara", "spa", "ita")

    @Test fun fewTracksShowAll() = assertNull(TrackPicks.common(listOf("eng", "fra", "deu"), setOf(0), listOf("eng")))

    @Test fun selectedAndPreferredFirstMatches() =
        assertEquals(listOf(1, 4), TrackPicks.common(many, setOf(4), listOf("eng")))

    @Test fun tvLanguageThenEnglish() =
        assertEquals(listOf(0, 1), TrackPicks.common(many, emptySet(), listOf("fra", "eng")))

    @Test fun nothingMatchesShowsTheFirstTwo() =
        assertEquals(listOf(0, 1), TrackPicks.common(listOf("a", "b", "c", "d", "e"), emptySet(), listOf("eng")))
}
