package com.hayat.lumen.ui.vod

/**
 * Turns Left/Right presses into seek steps that speed up, so crossing a two-hour film takes
 * seconds, not minutes. Holding speeds up by how long you've held (not how many key repeats
 * the remote sends, which varies by remote); quick taps in a row grow from 10 s to a minute.
 *
 * [now] is the key event's time in ms (any monotonic clock).
 */
class SeekRamp {
    private var direction = 0
    private var holdStart = 0L
    private var lastEvent = 0L
    private var taps = 0

    /** How far to move for this key event, in ms; negative is backwards. [repeat] = the key's repeat count. */
    fun step(direction: Int, repeat: Int, durationMs: Long, now: Long): Long {
        if (repeat == 0) {
            val streak = direction == this.direction && now - lastEvent < TAP_STREAK_MS
            taps = if (streak) taps + 1 else 0
            this.direction = direction
            holdStart = now
            lastEvent = now
            return direction * tapStep(taps)
        }
        val held = now - holdStart
        val sinceLast = (now - lastEvent).coerceIn(0L, MAX_GAP_MS)
        lastEvent = now
        return direction * (speed(held, durationMs) * sinceLast).toLong().coerceAtLeast(MIN_HOLD_STEP_MS)
    }

    companion object {
        private const val TAP_STREAK_MS = 1_000L
        private const val MAX_GAP_MS = 250L
        private const val MIN_HOLD_STEP_MS = 5_000L

        /** 1st–2nd tap 10 s, 3rd–4th 30 s, then a minute per tap. */
        fun tapStep(taps: Int): Long = when {
            taps < 2 -> 10_000L
            taps < 4 -> 30_000L
            else -> 60_000L
        }

        /**
         * Film time per real time while held: 1 min per second, then 4, then 10, then whichever
         * is faster of 20 min per second or a fifth of the film per second. Starting slow keeps
         * short holds precise; after 3 s you're clearly heading somewhere far.
         */
        fun speed(heldMs: Long, durationMs: Long): Double = when {
            heldMs < 1_000 -> 60.0
            heldMs < 2_000 -> 240.0
            heldMs < 3_000 -> 600.0
            else -> maxOf(1_200.0, durationMs / 5_000.0)
        }
    }
}
