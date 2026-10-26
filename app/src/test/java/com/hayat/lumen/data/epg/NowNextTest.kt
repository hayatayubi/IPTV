package com.hayat.lumen.data.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NowNextTest {
    private val a = Programme("A", null, start = 0, stop = 100)
    private val b = Programme("B", null, start = 100, stop = 200)
    private val c = Programme("C", null, start = 300, stop = 400)

    @Test
    fun picksCurrentAndFollowing() {
        assertEquals(NowNext(a, b), NowNext.from(listOf(a, b, c), at = 50))
        assertEquals(NowNext(b, c), NowNext.from(listOf(a, b, c), at = 100))
    }

    @Test
    fun gapHasOnlyNext() {
        assertEquals(NowNext(null, c), NowNext.from(listOf(a, b, c), at = 250))
    }

    @Test
    fun nothingLeft() {
        assertNull(NowNext.from(listOf(a, b, c), at = 500))
        assertEquals(0.5f, a.progress(50), 0f)
    }
}
