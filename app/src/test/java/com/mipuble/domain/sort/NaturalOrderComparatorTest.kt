package com.mipuble.domain.sort

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalOrderComparatorTest {

    private val comparator = NaturalOrderComparator()

    @Test
    fun `fixes the canonical Vol 1, 10, 2 bug`() {
        val shelved = listOf("Vol 1", "Vol 10", "Vol 11", "Vol 2", "Vol 3")

        val sorted = shelved.sortedWith(comparator)

        assertEquals(listOf("Vol 1", "Vol 2", "Vol 3", "Vol 10", "Vol 11"), sorted)
    }

    @Test
    fun `lexicographic sort really is broken - sanity check on the premise`() {
        val sorted = listOf("Vol 1", "Vol 2", "Vol 10").sorted()

        // The bug this comparator exists to fix:
        assertEquals(listOf("Vol 1", "Vol 10", "Vol 2"), sorted)
    }

    @Test
    fun `is case-insensitive for text`() {
        val sorted = listOf("banana", "Apple", "cherry").sortedWith(comparator)

        assertEquals(listOf("Apple", "banana", "cherry"), sorted)
    }

    @Test
    fun `leading zeros do not change numeric value`() {
        val sorted = listOf("Vol 08", "Vol 7", "Vol 9").sortedWith(comparator)

        assertEquals(listOf("Vol 7", "Vol 08", "Vol 9"), sorted)
    }

    @Test
    fun `leading zeros break exact numeric ties deterministically`() {
        // Same value, but distinct strings must not compare as equal.
        assertTrue(comparator.compare("Vol 1", "Vol 01") < 0)
    }

    @Test
    fun `compares multiple number segments independently`() {
        val sorted = listOf("2-10", "2-2", "10-1", "1-99").sortedWith(comparator)

        assertEquals(listOf("1-99", "2-2", "2-10", "10-1"), sorted)
    }

    @Test
    fun `handles numbers too large for Long without overflow`() {
        val big = "Book 99999999999999999999999999"
        val bigger = "Book 100000000000000000000000000"

        assertTrue(comparator.compare(big, bigger) < 0)
    }

    @Test
    fun `shorter string wins when one is a prefix of the other`() {
        assertTrue(comparator.compare("Vol 1", "Vol 1 — Special Edition") < 0)
    }

    @Test
    fun `numbers sort before letters at the same position`() {
        val sorted = listOf("Chapter B", "Chapter 2").sortedWith(comparator)

        assertEquals(listOf("Chapter 2", "Chapter B"), sorted)
    }

    @Test
    fun `equal strings compare as zero`() {
        assertEquals(0, comparator.compare("Vol 5", "Vol 5"))
    }

    @Test
    fun `mixed real-world series sorts in reading order`() {
        val library = listOf(
            "The Glass Archivist, Vol. 10",
            "The Glass Archivist, Vol. 1",
            "The Glass Archivist, Vol. 12",
            "The Glass Archivist, Vol. 2",
            "The Glass Archivist, Vol. 11",
        )

        val sorted = library.sortedWith(comparator)

        assertEquals(
            listOf(
                "The Glass Archivist, Vol. 1",
                "The Glass Archivist, Vol. 2",
                "The Glass Archivist, Vol. 10",
                "The Glass Archivist, Vol. 11",
                "The Glass Archivist, Vol. 12",
            ),
            sorted,
        )
    }
}
