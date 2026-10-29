package com.hayat.lumen.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryNamesTest {
    private fun check(raw: String, title: String, tags: List<String> = emptyList()) =
        assertEquals(DisplayName(title, tags), CategoryNames.display(raw))

    @Test fun superscriptTagsBecomeAChip() = check("TOP MOVIES ⁴ᴷ ³⁸⁴⁰ᴾ ᴰᴼᴸᴮʸ ᴬᵁᴰᴵᴼ", "Top Movies", listOf("4K"))

    @Test fun plainTags() = check("TOP MOVIES 4K 3840P DOLBY AUDIO", "Top Movies", listOf("4K"))

    @Test fun subtitleNotesGo() = check("ASIA SERIES (MULTI-SUBS)", "Asia Series")

    @Test fun bluray() = check("TOP KIDS BLURAY (MULTI-SUB)", "Top Kids Bluray")

    @Test fun brandsStayCapitalised() = check("OSN+ SERIES 4K 3840P", "OSN+ Series", listOf("4K"))

    @Test fun mixedCaseIsLeftAlone() = check("Crunchyroll Series", "Crunchyroll Series")
}
