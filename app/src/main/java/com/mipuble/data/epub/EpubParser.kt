package com.mipuble.data.epub

import com.mipuble.domain.model.EpubBook
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * Parses an EPUB by following the spec's pointer chain:
 *
 *   META-INF/container.xml  ->  the OPF package document
 *   OPF <metadata>          ->  title / author / cover reference
 *   OPF <manifest>          ->  id -> href lookup for every resource
 *   OPF <spine>             ->  ordered idrefs = the reading order
 *
 * Uses DOM (javax.xml) rather than Android's XmlPullParser so it stays pure
 * JVM code and can be unit-tested without Robolectric. Namespace-unaware, with
 * tag matching by local name, to tolerate the prefix variety found in the wild
 * (dc:title vs title, opf:meta vs meta, ...).
 */
class EpubParser @Inject constructor() {

    fun parse(file: File): EpubBook = ZipFile(file).use { zip ->
        val containerXml = zip.readEntry(CONTAINER_PATH)
            ?: error("Not a valid EPUB: missing $CONTAINER_PATH")
        val opfPath = parseOpfPath(containerXml)

        val opfXml = zip.readEntry(opfPath)
            ?: error("Not a valid EPUB: missing package document at $opfPath")
        parsePackage(opfXml, opfDir = EpubPaths.parentOf(opfPath))
    }

    private fun parseOpfPath(containerXml: ByteArray): String {
        val doc = containerXml.asDocument()
        val rootfile = doc.elementsByLocalName("rootfile").firstOrNull()
            ?: error("container.xml has no <rootfile>")
        return rootfile.getAttribute("full-path").also {
            require(it.isNotBlank()) { "container.xml <rootfile> has no full-path" }
        }
    }

    private fun parsePackage(opfXml: ByteArray, opfDir: String): EpubBook {
        val doc = opfXml.asDocument()

        val title = doc.firstTextByLocalName("title").orEmpty().trim()
        val author = doc.firstTextByLocalName("creator").orEmpty().trim()

        // id -> raw href, for both spine lookups and cover resolution.
        val manifest = doc.elementsByLocalName("item").associate { item ->
            item.getAttribute("id") to ManifestItem(
                href = item.getAttribute("href"),
                properties = item.getAttribute("properties"),
            )
        }

        val spineHrefs = doc.elementsByLocalName("itemref").mapNotNull { itemref ->
            manifest[itemref.getAttribute("idref")]?.href
                ?.let { EpubPaths.resolve(opfDir, it) }
        }

        val coverHref = findCoverHref(doc, manifest)?.let { EpubPaths.resolve(opfDir, it) }

        return EpubBook(
            title = title,
            author = author,
            spineHrefs = spineHrefs,
            coverImageHref = coverHref,
        )
    }

    /**
     * EPUB 3 marks the cover with properties="cover-image"; EPUB 2 uses a
     * <meta name="cover" content="<manifest-id>">. We support both.
     */
    private fun findCoverHref(doc: Document, manifest: Map<String, ManifestItem>): String? {
        manifest.values.firstOrNull { "cover-image" in it.properties.split(' ') }
            ?.let { return it.href }

        val coverId = doc.elementsByLocalName("meta")
            .firstOrNull { it.getAttribute("name") == "cover" }
            ?.getAttribute("content")
        return coverId?.let { manifest[it]?.href }
    }

    private data class ManifestItem(val href: String, val properties: String)

    private companion object {
        const val CONTAINER_PATH = "META-INF/container.xml"
    }
}

// --- DOM convenience -------------------------------------------------------

private fun ByteArray.asDocument(): Document =
    DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = false }
        .newDocumentBuilder()
        .parse(inputStream())
        .apply { documentElement.normalize() }

private fun Document.elementsByLocalName(localName: String): List<Element> {
    val nodes = getElementsByTagName("*")
    return (0 until nodes.length)
        .map { nodes.item(it) as Element }
        .filter { it.tagName.substringAfter(':') == localName }
}

private fun Document.firstTextByLocalName(localName: String): String? =
    elementsByLocalName(localName).firstOrNull()?.textContent
