package com.mipuble.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveParsingTest {

    @Test
    fun `parses files into remote books and strips the epub suffix`() {
        val json = """
            {"files":[
              {"id":"abc","name":"Piranesi.epub","size":"123456"},
              {"id":"def","name":"A Memory Called Empire.epub","size":"987654"}
            ]}
        """.trimIndent()

        val books = parseDriveFiles(json)

        assertEquals(2, books.size)
        assertEquals("abc", books[0].remoteId)
        assertEquals("Piranesi", books[0].title)
        assertEquals(123456L, books[0].sizeBytes)
        assertEquals("A Memory Called Empire", books[1].title)
    }

    @Test
    fun `tolerates missing size and skips entries without an id`() {
        val json = """
            {"files":[
              {"id":"x","name":"No Size.epub"},
              {"name":"No Id.epub","size":"10"}
            ]}
        """.trimIndent()

        val books = parseDriveFiles(json)

        assertEquals(1, books.size)
        assertEquals("x", books[0].remoteId)
        assertEquals(0L, books[0].sizeBytes)
    }

    @Test
    fun `empty or fieldless response yields no books`() {
        assertTrue(parseDriveFiles("""{"files":[]}""").isEmpty())
        assertTrue(parseDriveFiles("""{}""").isEmpty())
    }
}
