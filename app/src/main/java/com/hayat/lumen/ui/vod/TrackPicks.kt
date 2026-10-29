package com.hayat.lumen.ui.vod

/**
 * Which audio or subtitle tracks the panel shows before "More languages": films often carry a
 * dozen of each, and the subtitles sat below a long audio list.
 */
object TrackPicks {
    /** Up to this many tracks are simply all shown. */
    const val SHOW_ALL_UP_TO = 4

    /**
     * Indexes into [languages] (ISO 639-2 codes like "eng", or null) to show collapsed, in their
     * original order: the selected track, and the first track in each of [preferred] languages
     * (the TV's language, then English). Null means show everything.
     */
    fun common(languages: List<String?>, selected: Set<Int>, preferred: List<String>): List<Int>? {
        if (languages.size <= SHOW_ALL_UP_TO) return null
        val picks = selected.toMutableSet()
        for (language in preferred) {
            languages.indexOfFirst { it == language }.takeIf { it >= 0 }?.let { picks += it }
        }
        // Nothing matched (say, a film with only foreign tracks and none chosen yet): the first two.
        if (picks.isEmpty()) picks += listOf(0, 1)
        return picks.filter { it in languages.indices }.sorted().takeIf { it.size < languages.size }
    }
}
