package com.mipuble.data.epub

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EpubParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val parser = EpubParser()

    private fun epubFile(name: String = "book.epub") = tempFolder.newFile(name)

    @Test
    fun `reads title and author from OPF metadata`() {
        val file = epubFile().also { EpubTestFixtures.standardBook(it) }

        val book = parser.parse(file)

        assertEquals("The Glass Archivist", book.title)
        assertEquals("Mira Holt", book.author)
    }

    @Test
    fun `resolves spine hrefs to full zip paths in reading order`() {
        val file = epubFile().also { EpubTestFixtures.standardBook(it) }

        val book = parser.parse(file)

        assertEquals(
            listOf("OEBPS/text/c1.xhtml", "OEBPS/text/c2.xhtml", "OEBPS/text/c3.xhtml"),
            book.spineHrefs,
        )
    }

    @Test
    fun `spine order follows itemrefs, not manifest order`() {
        val file = epubFile()
        EpubTestFixtures.writeEpub(
            file,
            mapOf(
                "META-INF/container.xml" to EpubTestFixtures.container("content.opf"),
                "content.opf" to """
                    <?xml version="1.0"?>
                    <package xmlns="http://www.idpf.org/2007/opf">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>T</dc:title></metadata>
                      <manifest>
                        <item id="a" href="a.xhtml" media-type="application/xhtml+xml"/>
                        <item id="b" href="b.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine><itemref idref="b"/><itemref idref="a"/></spine>
                    </package>
                """.trimIndent(),
                "a.xhtml" to "A",
                "b.xhtml" to "B",
            ),
        )

        val book = parser.parse(file)

        assertEquals(listOf("b.xhtml", "a.xhtml"), book.spineHrefs)
    }

    @Test
    fun `resolves cover from EPUB3 cover-image property`() {
        val file = epubFile().also { EpubTestFixtures.standardBook(it) }

        val book = parser.parse(file)

        assertEquals("OEBPS/images/cover.png", book.coverImageHref)
    }

    @Test
    fun `resolves cover from EPUB2 meta name cover`() {
        val file = epubFile()
        EpubTestFixtures.writeEpub(
            file,
            mapOf(
                "META-INF/container.xml" to EpubTestFixtures.container("OEBPS/content.opf"),
                "OEBPS/content.opf" to """
                    <?xml version="1.0"?>
                    <package xmlns="http://www.idpf.org/2007/opf">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>T</dc:title>
                        <meta name="cover" content="mycover"/>
                      </metadata>
                      <manifest>
                        <item id="mycover" href="cover.jpg" media-type="image/jpeg"/>
                        <item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine><itemref idref="c1"/></spine>
                    </package>
                """.trimIndent(),
                "OEBPS/cover.jpg" to "JPG",
                "OEBPS/c1.xhtml" to "C1",
            ),
        )

        val book = parser.parse(file)

        assertEquals("OEBPS/cover.jpg", book.coverImageHref)
    }

    @Test
    fun `book without a cover yields null cover href`() {
        val file = epubFile()
        EpubTestFixtures.writeEpub(
            file,
            mapOf(
                "META-INF/container.xml" to EpubTestFixtures.container("content.opf"),
                "content.opf" to """
                    <?xml version="1.0"?>
                    <package xmlns="http://www.idpf.org/2007/opf">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>T</dc:title></metadata>
                      <manifest><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/></manifest>
                      <spine><itemref idref="c1"/></spine>
                    </package>
                """.trimIndent(),
                "c1.xhtml" to "C1",
            ),
        )

        assertNull(parser.parse(file).coverImageHref)
    }

    @Test(expected = IllegalStateException::class)
    fun `rejects a file with no container xml`() {
        val file = epubFile()
        EpubTestFixtures.writeEpub(file, mapOf("random.txt" to "not an epub"))

        parser.parse(file)
    }
}
