package com.mipuble.domain.title

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesCatalogTest {

    private val catalog = SeriesCatalog(
        listOf(
            "Spice and Wolf",
            "Re:Zero - Starting Life in Another World",
            "Mob Psycho 100",
            "Berserk",
            "The Apothecary Diaries",
        ),
    )

    @Test
    fun `exact name matches with full score`() {
        val m = catalog.match("Spice and Wolf")
        assertEquals("Spice and Wolf", m?.canonical)
        assertEquals(1f, m!!.score, 0.0001f)
    }

    @Test
    fun `merged-word filename still matches via character ratio`() {
        // "SpiceWolf" has no shared tokens with "Spice and Wolf" but is close
        // once both are collapsed to spaceless form.
        val m = catalog.match("SpiceWolf")
        assertEquals("Spice and Wolf", m?.canonical)
        assertTrue("score ${m?.score}", m!!.score >= 0.7f)
    }

    @Test
    fun `abbreviated query matches longer official name via token coverage`() {
        val m = catalog.match("Re Zero")
        assertEquals("Re:Zero - Starting Life in Another World", m?.canonical)
        assertEquals(1f, m!!.score, 0.0001f)
    }

    @Test
    fun `a word merely embedded in the query does not force a match`() {
        // "air" sits inside "repair"; the merged-word rule must not match
        // "Air Gear" highly for an unrelated "Repair Gear Manual".
        val c = SeriesCatalog(listOf("Air Gear"))
        val m = c.match("Repair Gear Manual")
        assertTrue("score ${m?.score}", m!!.score < 0.7f)
    }

    @Test
    fun `unrelated query does not score highly`() {
        val m = catalog.match("Completely Unrelated Thing")
        assertNotNull(m) // a closest match is always offered…
        assertTrue("score ${m?.score}", m!!.score < 0.7f) // …but with a low score
    }

    @Test
    fun `ranked returns best first and respects the limit`() {
        val results = catalog.ranked("Apothecary", limit = 2)
        assertEquals(2, results.size)
        assertEquals("The Apothecary Diaries", results.first().canonical)
    }

    @Test
    fun `search prioritizes substring hits, shortest first`() {
        val c = SeriesCatalog(listOf("GANTZ", "GANTZ:G", "A Certain Magical Index"))
        val hits = c.search("gantz", limit = 5)
        assertEquals(listOf("GANTZ", "GANTZ:G"), hits)
    }

    @Test
    fun `blank search returns nothing`() {
        assertTrue(catalog.search("   ", limit = 5).isEmpty())
    }

    @Test
    fun `empty catalog yields no match`() {
        assertEquals(null, SeriesCatalog(emptyList()).match("anything"))
    }

    @Test
    fun `duplicate names collapse case-insensitively`() {
        assertEquals(1, SeriesCatalog(listOf("Berserk", "berserk", "BERSERK")).size)
    }
}
