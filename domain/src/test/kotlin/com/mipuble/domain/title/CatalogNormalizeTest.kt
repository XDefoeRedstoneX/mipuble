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
        assertEquals(4, n.volume)
        assertEquals("Spice and Wolf, Vol. 4", n.displayTitle)
        assertEquals("spiceandwolf|4", n.dedupKey)
        assertEquals(MatchConfidence.AUTO, n.confidence)
    }

    @Test
    fun `catalog match unlocks bare-number volumes`() {
        // No explicit "v"/"vol" marker — only safe to read 4 as a volume because
        // the series matched the catalog.
        val n = TitleNormalizer.normalize("Spice and Wolf 4", catalog)
        assertEquals(4, n.volume)
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
    fun `unknown title with a trailing number is not mangled and goes to review`() {
        // "Fahrenheit 451" isn't in the catalog, so it must NOT auto-rename and
        // must NOT treat 451 as a volume.
        val n = TitleNormalizer.normalize("Fahrenheit 451", catalog)
        assertNull(n.volume)
        assertEquals(MatchConfidence.REVIEW, n.confidence)
        assertTrue(n.suggestions.isNotEmpty())
    }

    @Test
    fun `weak match keeps the cleaned original but offers suggestions`() {
        val n = TitleNormalizer.normalize("Totally Made Up Series", catalog)
        assertEquals("Totally Made Up Series", n.series)
        assertEquals(MatchConfidence.REVIEW, n.confidence)
        assertTrue(n.suggestions.isNotEmpty())
    }

    @Test
    fun `no catalog reproduces the plain behavior`() {
        val n = TitleNormalizer.normalize("Fahrenheit 451")
        assertEquals(MatchConfidence.NONE, n.confidence)
        assertNull(n.volume)
        assertEquals("Fahrenheit 451", n.displayTitle)
    }
}
