package com.hayat.lumen.data.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.StringReader

class XmltvParserTest {

    private fun parse(xml: String): Pair<List<XmltvProgramme>, Int> {
        val pull = KXmlParser().apply { setInput(StringReader(xml)) }
        val parser = XmltvParser(pull)
        return parser.programmes().toList() to parser.skipped
    }

    @Test
    fun readsProgrammes() {
        val (programmes, skipped) = parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE tv SYSTEM "xmltv.dtd">
            <tv generator-info-name="test">
              <channel id="BBC1.uk"><display-name>BBC One</display-name></channel>
              <programme start="20261026200000 +0000" stop="20261026210000 +0000" channel="BBC1.uk">
                <title lang="en">News &amp; Weather</title>
                <title lang="cy">Newyddion</title>
                <desc lang="en">The latest.</desc>
                <category>News</category>
              </programme>
              <programme start="20261026210000 +0100" stop="20261026213000 +0100" channel="bbc1.uk">
                <title>Late show</title>
              </programme>
            </tv>
            """.trimIndent(),
        )
        assertEquals(0, skipped)
        assertEquals(2, programmes.size)
        assertEquals(XmltvProgramme("bbc1.uk", 1793044800000, 1793048400000, "News & Weather", "The latest."), programmes[0])
        assertEquals(1793044800000, programmes[1].start) // 21:00 +0100 == 20:00 UTC
        assertNull(programmes[1].description)
    }

    @Test
    fun skipsBrokenProgrammes() {
        val (programmes, skipped) = parse(
            """
            <tv>
              <programme start="nonsense" stop="20261026210000 +0000" channel="a"><title>x</title></programme>
              <programme start="20261026200000 +0000" stop="20261026210000 +0000" channel="a"></programme>
              <programme start="20261026200000 +0000" stop="20261026210000 +0000"><title>x</title></programme>
              <programme start="20261026200000 +0000" stop="20261026210000 +0000" channel="a"><title>ok</title></programme>
            </tv>
            """.trimIndent(),
        )
        assertEquals(3, skipped)
        assertEquals(listOf("ok"), programmes.map { it.title })
    }

    @Test
    fun parsesTimeVariants() {
        assertEquals(1793044800000, XmltvParser.parseTime("20261026200000 +0000"))
        assertEquals(1793044800000, XmltvParser.parseTime("20261026200000"))
        assertEquals(1793044800000, XmltvParser.parseTime("202610262000"))
        assertEquals(1793044800000, XmltvParser.parseTime("20261026150000 -0500"))
        assertNull(XmltvParser.parseTime("2026"))
    }
}
