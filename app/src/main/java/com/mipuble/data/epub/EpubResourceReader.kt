package com.mipuble.data.epub

import java.io.Closeable
import java.io.File
import java.util.zip.ZipFile

/**
 * Serves individual resources (chapters, CSS, images, fonts) out of an EPUB
 * zip on demand. Holds one open [ZipFile] for the reading session so the
 * WebView can fan out many resource requests per chapter cheaply; closed when
 * the reader goes away. A book is never fully unpacked to disk.
 */
class EpubResourceReader(file: File) : Closeable {

    private val zip = ZipFile(file)

    fun read(entryPath: String): ByteArray? {
        val normalized = EpubPaths.normalize(entryPath)
        val entry = zip.getEntry(normalized) ?: return null
        return zip.getInputStream(entry).use { it.readBytes() }
    }

    /**
     * Last-resort cover when the EPUB declares none and the opening pages
     * yield nothing: scan the archive for a raster image, preferring one whose
     * name contains "cover", otherwise the largest (cover art is usually the
     * biggest image in the book). SVG is skipped — the image loader can't
     * decode it, and a vector "cover" is almost always a wrapper page anyway.
     */
    fun fallbackRasterCover(): Pair<String, ByteArray>? {
        val images = zip.entries().asSequence()
            .filter { !it.isDirectory && RASTER_IMAGE.containsMatchIn(it.name) }
            .toList()
        if (images.isEmpty()) return null
        val chosen = images.firstOrNull { it.name.contains("cover", ignoreCase = true) }
            ?: images.maxByOrNull { it.size }
            ?: return null
        val bytes = zip.getInputStream(chosen).use { it.readBytes() }
        return chosen.name to bytes
    }

    override fun close() = zip.close()

    private companion object {
        val RASTER_IMAGE = Regex("""\.(jpe?g|png|webp|gif)$""", RegexOption.IGNORE_CASE)
    }
}

/** Reads a single entry's bytes, or null if it isn't present. */
internal fun ZipFile.readEntry(entryPath: String): ByteArray? {
    val entry = getEntry(entryPath) ?: return null
    return getInputStream(entry).use { it.readBytes() }
}
