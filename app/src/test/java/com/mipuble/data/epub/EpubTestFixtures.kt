package com.mipuble.data.epub

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds real EPUB zip files on disk for parser tests, so we exercise the
 * actual ZipFile + DOM path rather than mocking it.
 */
object EpubTestFixtures {

    fun writeEpub(file: File, entries: Map<String, String>) {
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }

    fun container(opfPath: String) = """
        <?xml version="1.0"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles>
            <rootfile full-path="$opfPath" media-type="application/oebps-package+xml"/>
          </rootfiles>
        </container>
    """.trimIndent()

    /** A 3-chapter EPUB 3 book under OEBPS/ with a cover-image property. */
    fun standardBook(file: File) {
        writeEpub(
            file,
            mapOf(
                "META-INF/container.xml" to container("OEBPS/content.opf"),
                "OEBPS/content.opf" to """
                    <?xml version="1.0"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>The Glass Archivist</dc:title>
                        <dc:creator>Mira Holt</dc:creator>
                      </metadata>
                      <manifest>
                        <item id="cov" href="images/cover.png" media-type="image/png" properties="cover-image"/>
                        <item id="c1" href="text/c1.xhtml" media-type="application/xhtml+xml"/>
                        <item id="c2" href="text/c2.xhtml" media-type="application/xhtml+xml"/>
                        <item id="c3" href="text/c3.xhtml" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine>
                        <itemref idref="c1"/>
                        <itemref idref="c2"/>
                        <itemref idref="c3"/>
                      </spine>
                    </package>
                """.trimIndent(),
                "OEBPS/text/c1.xhtml" to "<html><body>One</body></html>",
                "OEBPS/text/c2.xhtml" to "<html><body>Two</body></html>",
                "OEBPS/text/c3.xhtml" to "<html><body>Three</body></html>",
                "OEBPS/images/cover.png" to "PNGBYTES",
            ),
        )
    }
}
