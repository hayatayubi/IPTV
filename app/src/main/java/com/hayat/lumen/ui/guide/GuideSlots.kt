package com.hayat.lumen.ui.guide

import com.hayat.lumen.data.epg.Programme

/**
 * One cell of a channel's row in the guide grid: a programme, or a stretch with no guide
 * info ([programme] null). A row's slots run back to back from the grid's start to its end,
 * so Left/Right always has somewhere to go.
 */
data class Slot(val start: Long, val stop: Long, val programme: Programme?) {
    operator fun contains(time: Long) = time in start until stop
}

object GuideSlots {
    const val HALF_HOUR = 30 * 60_000L

    /** [time] rounded down to the half hour (:00 or :30). */
    fun floorHalfHour(time: Long) = time - Math.floorMod(time, HALF_HOUR)

    /**
     * Lays [programmes] (sorted by start) out from [from] to [until]. Gaps become empty slots, cut
     * at each half hour so they're no wider than a column. Programmes that overlap the one before
     * (sloppy guides do this) start where it ends; ones left with no time are dropped.
     */
    fun build(programmes: List<Programme>, from: Long, until: Long): List<Slot> {
        val out = ArrayList<Slot>()
        var cursor = from
        fun gapUntil(end: Long) {
            while (cursor < end) {
                val next = minOf(end, floorHalfHour(cursor) + HALF_HOUR)
                out += Slot(cursor, next, null)
                cursor = next
            }
        }
        for (p in programmes) {
            if (p.stop <= cursor || p.start >= until) continue
            gapUntil(p.start)
            val start = maxOf(p.start, cursor)
            if (p.stop > start) {
                out += Slot(start, p.stop, p)
                cursor = p.stop
            }
        }
        gapUntil(until)
        return out
    }

    /** The slot showing at [time], else the nearest one. -1 only when [slots] is empty. */
    fun indexAt(slots: List<Slot>, time: Long): Int {
        if (slots.isEmpty()) return -1
        val i = slots.indexOfFirst { time < it.stop }
        return if (i < 0) slots.lastIndex else i
    }
}
