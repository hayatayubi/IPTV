package com.hayat.lumen.ui.guide

import com.hayat.lumen.data.epg.Programme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuideSlotsTest {
    private val min = 60_000L
    private val from = 1_000 * GuideSlots.HALF_HOUR // on a half hour
    private fun at(minutes: Int) = from + minutes * min
    private fun p(title: String, start: Int, stop: Int) = Programme(title, null, at(start), at(stop))

    @Test fun emptyRowIsHalfHourGaps() {
        val slots = GuideSlots.build(emptyList(), from, at(90))
        assertEquals(listOf(at(0), at(30), at(60)), slots.map { it.start })
        assert(slots.all { it.programme == null })
    }

    @Test fun gapsAreFilledAndCutAtHalfHours() {
        val slots = GuideSlots.build(listOf(p("A", 10, 40), p("B", 75, 90)), from, at(90))
        assertEquals(listOf(null, "A", null, null, "B"), slots.map { it.programme?.title })
        assertEquals(listOf(at(0), at(10), at(40), at(60), at(75)), slots.map { it.start })
    }

    @Test fun programmeBeforeTheGridIsClippedAndOverlapsTrimmed() {
        val slots = GuideSlots.build(listOf(p("A", -20, 20), p("B", 15, 50), p("C", 20, 40)), from, at(60))
        assertEquals(listOf("A", "B", null), slots.map { it.programme?.title })
        assertEquals(at(0), slots[0].start)
        assertEquals(at(20), slots[1].start) // B starts where A ends; C fits inside B and is dropped
    }

    @Test fun indexAtFindsTheSlotShowingThenTheNearest() {
        val slots = GuideSlots.build(listOf(p("A", 0, 30)), from, at(60))
        assertEquals(0, GuideSlots.indexAt(slots, at(10)))
        assertEquals(1, GuideSlots.indexAt(slots, at(30)))
        assertEquals(1, GuideSlots.indexAt(slots, at(999)))
        assertEquals(-1, GuideSlots.indexAt(emptyList(), at(0)))
        assertNull(slots[1].programme)
    }
}
