package com.hayat.lumen.ui.vod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekRampTest {
    private val film = 2 * 60 * 60 * 1000L

    @Test
    fun singleTapsMoveTenSeconds() {
        val ramp = SeekRamp()
        assertEquals(10_000L, ramp.step(+1, 0, film, now = 0))
        assertEquals(-10_000L, ramp.step(-1, 0, film, now = 5_000))
        assertEquals(10_000L, ramp.step(+1, 0, film, now = 10_000))
    }

    @Test
    fun quickTapsSpeedUp() {
        val ramp = SeekRamp()
        val steps = (0 until 6).map { ramp.step(+1, 0, film, now = it * 300L) }
        assertEquals(listOf(10_000L, 10_000L, 30_000L, 30_000L, 60_000L, 60_000L), steps)
        // Changing direction starts again.
        assertEquals(-10_000L, ramp.step(-1, 0, film, now = 2_000))
    }

    @Test
    fun holdingCrossesAFilmInSeconds() {
        val ramp = SeekRamp()
        var moved = ramp.step(+1, 0, film, now = 0)
        // A remote repeating every 100 ms after a 400 ms delay, held for 8 seconds.
        var t = 400L
        var repeat = 1
        while (t <= 8_000) {
            moved += ramp.step(+1, repeat++, film, now = t)
            t += 100
        }
        assertTrue("moved $moved ms", moved >= film)
    }

    @Test
    fun holdSpeedDoesNotDependOnTheRepeatRate() {
        fun after(heldMs: Long, everyMs: Long): Long {
            val ramp = SeekRamp()
            var moved = ramp.step(+1, 0, film, 0)
            var t = everyMs
            var repeat = 1
            while (t <= heldMs) {
                moved += ramp.step(+1, repeat++, film, t)
                t += everyMs
            }
            return moved
        }
        val slow = after(4_000, 100)
        val fast = after(4_000, 50)
        assertTrue("slow=$slow fast=$fast", kotlin.math.abs(slow - fast) < 60_000)
    }
}
