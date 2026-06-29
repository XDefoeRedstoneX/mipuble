package com.mipuble.domain.title

import com.mipuble.domain.title.TitleNormalizer.MatchConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Catalog-aware behavior of [TitleNormalizer.normalize]. */
class CatalogNormalizeTest {

    private val catalog = SeriesCatalog(
        listOf(
            "Spice and Wolf",
            "Mob Psycho 100",
            "Berserk",
        ),
    )

    @Test
    fun `confident match snaps the series to the official name`() {
        val n = TitleNormalizer.normalize("SpiceWolf v04 [Premium]", catalog)
        assertEquals("Spice and Wolf", n.series)
        assertEquals("4", n.volume)
        assertEquals("Spice and Wolf, Vol. 4", n.displayTitle)
        assertEquals("spiceandwolf|4", n.dedupKey)
        assertEquals(MatchConfidence.AUTO, n.confidence)
    }

    @Test
    fun `catalog match unlocks bare-number volumes`() {
        // No explicit "v"/"vol" marker — only safe to read 4 as a volume because
        // the series matched the catalog.
        val n = TitleNormalizer.normalize("Spice and Wolf 4", catalog)
        assertEquals("4", n.volume)
        assertEquals("spiceandwolf|4", n.dedupKey)
        assertEquals(MatchConfidence.AUTO, n.confidence)
    }

    @Test
    fun `a number that is part of the official name is not a volume`() {
        val n = TitleNormalizer.normalize("Mob Psycho 100", catalog)
        assertEquals("Mob Psycho 100", n.series)
        assertNull(n.volume)
        assertEquals(MatchConfidence.AUTO, n.confidence)
    }

    @Test
    fun `a digit inside the official name does not block a real trailing volume`() {
        // "86―EIGHTY-SIX" contains '8' and '6', but neither is a standalone "6";
        // a trailing "6" is still a genuine volume.
        val c = SeriesCatalog(listOf("86―EIGHTY-SIX"))
        val n = TitleNormalizer.normalize("86―EIGHTY-SIX 6", c)
        assertEquals(MatchConfidence.AUTO, n.confidence)
        assertEquals("6", n.volume)
    }

    @Test
    fun `unknown title with a trailing number is not mangled and goes to review`() {
        // "Fahrenheit 451" isn't in the catalog, so it must NOT auto-rename and
        // must NOT treat 451 as a volume.
        val n = TitleNormalizer.normalize("Fahrenheit 451", catalog)
        assertNull(n.volume)
        assertEquals(MatchConfidence.REVIEW, n.confidence)
    }

    @Test
    fun `a wholly unrelated title is reviewed but offers no weak suggestions`() {
        // Nothing in the catalog is close, so we don't pre-select a wrong name;
        // the row keeps the original and lets the user search.
        val n = TitleNormalizer.normalize("Totally Made Up Series", catalog)
        assertEquals("Totally Made Up Series", n.series)
        assertEquals(MatchConfidence.REVIEW, n.confidence)
        assertTrue(n.suggestions.isEmpty())
    }

    @Test
    fun `a near-miss is reviewed and does offer suggestions`() {
        // Close enough to clear the suggestion floor without being auto-confident.
        val n = TitleNormalizer.normalize("Mob Psych", catalog)
        assertEquals(MatchConfidence.REVIEW, n.confidence)
        assertTrue(n.suggestions.contains("Mob Psycho 100"))
    }

    @Test
    fun `no catalog reproduces the plain behavior`() {
        val n = TitleNormalizer.normalize("Fahrenheit 451")
        assertEquals(MatchConfidence.NONE, n.confidence)
        assertNull(n.volume)
        assertEquals("Fahrenheit 451", n.displayTitle)
    }
}
