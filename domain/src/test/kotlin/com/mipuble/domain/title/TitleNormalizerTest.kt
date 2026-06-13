package com.mipuble.domain.title

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleNormalizerTest {

    @Test
    fun `strips tags and extracts volume`() {
        val n = TitleNormalizer.normalize("xxx Vol 1 [Premium]{Translated}")
        assertEquals("xxx", n.series)
        assertEquals(1, n.volume)
        assertEquals("xxx, Vol. 1", n.displayTitle)
        assertEquals("xxx|1", n.dedupKey)
    }

    @Test
    fun `drops trailing junk after the volume`() {
        val n = TitleNormalizer.normalize("xxx v2 - rahhh")
        assertEquals("xxx", n.series)
        assertEquals(2, n.volume)
        assertEquals("xxx, Vol. 2", n.displayTitle)
        assertEquals("xxx|2", n.dedupKey)
    }

    @Test
    fun `same series different volumes are not duplicates`() {
        val a = TitleNormalizer.normalize("xxx Vol 1 [Premium]{Translated}")
        val b = TitleNormalizer.normalize("xxx v2 - rahhh")
        assertEquals("xxx|1", a.dedupKey)
        assertEquals("xxx|2", b.dedupKey)
    }

    @Test
    fun `same volume from different sources shares a dedup key`() {
        val a = TitleNormalizer.normalize("xxx Vol 1 [Premium]{Translated}")
        val b = TitleNormalizer.normalize("xxx Vol 1 (HQ scan)")
        assertEquals(a.dedupKey, b.dedupKey)
    }

    @Test
    fun `key ignores case and punctuation in the series`() {
        val a = TitleNormalizer.normalize("Re:Zero v05 (Yen Press)")
        val b = TitleNormalizer.normalize("re zero vol. 5")
        assertEquals(5, a.volume)
        assertEquals("Re:Zero", a.series)
        assertEquals(a.dedupKey, b.dedupKey)
    }

    @Test
    fun `leading zeros normalize to the same number`() {
        assertEquals(5, TitleNormalizer.normalize("Series v05").volume)
        assertEquals("series|5", TitleNormalizer.normalize("Series v05").dedupKey)
    }

    @Test
    fun `hash markers count as volumes`() {
        assertEquals(3, TitleNormalizer.normalize("Some Comic #3").volume)
    }

    @Test
    fun `existing tidy titles are preserved`() {
        val n = TitleNormalizer.normalize("The Glass Archivist, Vol. 10")
        assertEquals("The Glass Archivist", n.series)
        assertEquals(10, n.volume)
        assertEquals("The Glass Archivist, Vol. 10", n.displayTitle)
    }

    @Test
    fun `volume-less standalone has no dedup key`() {
        val n = TitleNormalizer.normalize("Piranesi")
        assertEquals("Piranesi", n.series)
        assertNull(n.volume)
        assertNull(n.dedupKey)
        assertEquals("Piranesi", n.displayTitle)
    }

    @Test
    fun `four digit trailing number is not mistaken for a volume`() {
        // No explicit marker; a year-like trailing number should be ignored.
        assertNull(TitleNormalizer.normalize("Some Book 2021").volume)
    }
}
