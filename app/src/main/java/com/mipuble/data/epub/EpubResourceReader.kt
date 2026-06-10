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

    override fun close() = zip.close()
}

/** Reads a single entry's bytes, or null if it isn't present. */
internal fun ZipFile.readEntry(entryPath: String): ByteArray? {
    val entry = getEntry(entryPath) ?: return null
    return getInputStream(entry).use { it.readBytes() }
}
