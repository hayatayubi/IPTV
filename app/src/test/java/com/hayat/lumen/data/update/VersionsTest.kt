package com.hayat.lumen.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsTest {
    @Test
    fun parsesTagsAndNames() {
        assertEquals(listOf(0, 2, 0), Versions.parse("v0.2.0"))
        assertEquals(listOf(1, 10), Versions.parse("1.10"))
        assertNull(Versions.parse("nightly"))
        assertNull(Versions.parse("v1.2.0-beta"))
    }

    @Test
    fun comparesNumbersNotText() {
        assertTrue(Versions.isNewer("v0.10.0", "0.9.0"))
        assertTrue(Versions.isNewer("v0.2.1", "0.2.0"))
        assertTrue(Versions.isNewer("1.0", "0.9.9"))
        assertFalse(Versions.isNewer("v0.2.0", "0.2.0"))
        assertFalse(Versions.isNewer("v0.2", "0.2.0"))
        assertFalse(Versions.isNewer("v0.1.9", "0.2.0"))
    }

    @Test
    fun ignoresTagsItCannotRead() {
        assertFalse(Versions.isNewer("latest", "0.2.0"))
        assertFalse(Versions.isNewer("v0.3.0", "dev"))
    }
}
