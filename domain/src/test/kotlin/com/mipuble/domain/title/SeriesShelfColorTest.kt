package com.mipuble.domain.title

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesShelfColorTest {

    @Test
    fun `same name always maps to the same color`() {
        assertEquals(
            SeriesShelfColor.forName("Spice and Wolf"),
            SeriesShelfColor.forName("Spice and Wolf"),
        )
    }

    @Test
    fun `color is case-insensitive`() {
        assertEquals(
            SeriesShelfColor.forName("Berserk"),
            SeriesShelfColor.forName("berserk"),
        )
    }

    @Test
    fun `color is fully opaque`() {
        val alpha = (SeriesShelfColor.forName("Anything") ushr 24) and 0xFF
        assertEquals(0xFF, alpha)
    }

    @Test
    fun `different names can pick different colors`() {
        // Not guaranteed per-pair, but the palette must produce more than one value.
        val colors = (1..40).map { SeriesShelfColor.forName("Series $it") }.toSet()
        assertTrue(colors.size > 1)
    }
}
