package com.hayat.lumen.data.epg

import org.xmlpull.v1.XmlPullParser

data class XmltvProgramme(
    /** The `channel` attribute, lowercased so it matches `lower(channels.tvgId)`. */
    val channel: String,
    val start: Long,
    val stop: Long,
    val title: String,
    val description: String?,
)

/**
 * Reads `<programme>` entries from an XMLTV guide, lazily: a provider's full guide can be
 * hundreds of MB, so it's pulled one element at a time and never held in memory.
 *
 * The caller supplies the pull parser (Android's `Xml.newPullParser()`, kXML in unit tests)
 * already pointed at the input.
 */
class XmltvParser(private val parser: XmlPullParser) {

    /** Programmes that failed to parse (bad times, no title). */
    var skipped = 0
        private set

    fun programmes(): Sequence<XmltvProgramme> = sequence {
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "programme") {
                val programme = readProgramme()
                if (programme != null) yield(programme) else skipped++
            }
            event = parser.next()
        }
    }

    /** Reads one `<programme>`, leaving the parser on its end tag. */
    private fun readProgramme(): XmltvProgramme? {
        val channel = parser.getAttributeValue(null, "channel")?.trim()?.lowercase()
        val start = parser.getAttributeValue(null, "start")?.let(::parseTime)
        val stop = parser.getAttributeValue(null, "stop")?.let(::parseTime)
        var title: String? = null
        var description: String? = null
        val depth = parser.depth
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.END_TAG && parser.depth == depth) break
            if (event != XmlPullParser.START_TAG || parser.depth != depth + 1) continue
            // Feeds may repeat title/desc per language; the first one wins.
            when (parser.name) {
                "title" -> parser.nextText().trim().let { if (title == null && it.isNotEmpty()) title = it }
                "desc" -> parser.nextText().trim().let { if (description == null && it.isNotEmpty()) description = it }
            }
        }
        if (channel.isNullOrEmpty() || start == null || stop == null || stop <= start) return null
        return XmltvProgramme(channel, start, stop, title ?: return null, description)
    }

    companion object {
        /**
         * XMLTV time, e.g. `20261026220000 +0100`, to epoch millis. Seconds and minutes may be
         * missing; no offset means UTC. Null if unreadable.
         */
        fun parseTime(raw: String): Long? {
            val text = raw.trim()
            val digits = text.takeWhile { it.isDigit() }
            if (digits.length < 8) return null
            fun part(from: Int, len: Int) = if (digits.length >= from + len) digits.substring(from, from + len).toInt() else 0
            val offset = text.substring(digits.length).trim().let { rest ->
                if (rest.length >= 5 && (rest[0] == '+' || rest[0] == '-') && rest.substring(1, 5).all { it.isDigit() }) {
                    val minutes = rest.substring(1, 3).toInt() * 60 + rest.substring(3, 5).toInt()
                    if (rest[0] == '-') -minutes else minutes
                } else 0
            }
            return runCatching {
                java.time.LocalDateTime.of(part(0, 4), part(4, 2), part(6, 2), part(8, 2), part(10, 2), part(12, 2))
                    .toEpochSecond(java.time.ZoneOffset.ofTotalSeconds(offset * 60)) * 1000
            }.getOrNull()
        }
    }
}
