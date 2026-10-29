package com.hayat.lumen.ui.player

import com.hayat.lumen.data.epg.Programme
import org.junit.Assert.assertEquals
import org.junit.Test

class GuidePickTest {
    private fun p(start: Long, stop: Long) = Programme("p$start", null, start, stop)
    private val day = listOf(p(100, 200), p(200, 300), p(400, 500))

    @Test fun picksTheProgrammeOnAtThatTime() {
        assertEquals(0, GuidePick.index(day, 100))
        assertEquals(1, GuidePick.index(day, 250))
    }

    @Test fun inAGapPicksTheNextOne() = assertEquals(2, GuidePick.index(day, 350))

    @Test fun beforeTheFirstPicksTheFirst() = assertEquals(0, GuidePick.index(day, 10))

    @Test fun afterTheLastPicksTheLast() = assertEquals(2, GuidePick.index(day, 900))

    @Test fun nothingKnown() = assertEquals(-1, GuidePick.index(emptyList(), 100))
}
