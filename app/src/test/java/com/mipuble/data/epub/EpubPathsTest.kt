package com.mipuble.data.epub

import org.junit.Assert.assertEquals
import org.junit.Test

class EpubPathsTest {

    @Test
    fun `resolves href relative to the OPF directory`() {
        assertEquals("OEBPS/text/c1.xhtml", EpubPaths.resolve("OEBPS", "text/c1.xhtml"))
    }

    @Test
    fun `resolves href from the zip root when OPF is at the root`() {
        assertEquals("c1.xhtml", EpubPaths.resolve("", "c1.xhtml"))
    }

    @Test
    fun `collapses parent-directory segments`() {
        // A chapter in OEBPS/text referencing ../images/cover.png
        assertEquals("OEBPS/images/cover.png", EpubPaths.resolve("OEBPS/text", "../images/cover.png"))
    }

    @Test
    fun `treats a leading slash as zip-root absolute`() {
        assertEquals("images/cover.png", EpubPaths.resolve("OEBPS/text", "/images/cover.png"))
    }

    @Test
    fun `normalize drops dot segments`() {
        assertEquals("a/b.xhtml", EpubPaths.normalize("a/./b.xhtml"))
    }

    @Test
    fun `parentOf returns empty for a root-level entry`() {
        assertEquals("", EpubPaths.parentOf("content.opf"))
        assertEquals("OEBPS", EpubPaths.parentOf("OEBPS/content.opf"))
    }
}
