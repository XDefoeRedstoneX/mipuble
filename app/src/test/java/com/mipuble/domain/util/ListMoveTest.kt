package com.mipuble.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ListMoveTest {

    @Test
    fun `moving forward shifts the items between`() {
        assertEquals(listOf("b", "c", "a", "d"), listOf("a", "b", "c", "d").move(0, 2))
    }

    @Test
    fun `moving backward shifts the items between`() {
        assertEquals(listOf("d", "a", "b", "c"), listOf("a", "b", "c", "d").move(3, 0))
    }

    @Test
    fun `moving to the same index returns the same list`() {
        val list = listOf("a", "b")
        assertSame(list, list.move(1, 1))
    }

    @Test
    fun `out of range indices are ignored`() {
        val list = listOf("a", "b")
        assertSame(list, list.move(0, 5))
        assertSame(list, list.move(-1, 1))
    }
}
