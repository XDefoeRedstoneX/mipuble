package com.mipuble.data.epub

import android.content.Context
import android.net.Uri
import com.mipuble.data.local.BookDao
import com.mipuble.data.local.BookEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Brings an EPUB into the library: copies the file into app-private storage,
 * parses its metadata, extracts the cover, and records a [BookEntity] pointing
 * at the stored file. The book's bytes live on disk exactly once; the reader
 * streams chapters straight from this copy.
 */
@Singleton
class EpubImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parser: EpubParser,
    private val bookDao: BookDao,
) {

    /** Imports from a SAF content Uri chosen by the user. */
    suspend fun import(uriString: String): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(uriString)
            val target = File(booksDir(), "${UUID.randomUUID()}.epub")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            } ?: error("Could not open $uriString")
            finishImport(target)
        }
    }

    /** Imports a sample book bundled in assets; used to seed the demo library. */
    suspend fun importFromAsset(assetName: String): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(booksDir(), assetName)
            if (!target.exists()) {
                context.assets.open(assetName).use { input ->
                    target.outputStream().use { input.copyTo(it) }
                }
            }
            finishImport(target)
        }
    }

    /** Copies an arbitrary file into book storage; used by the Drive uploader. */
    fun copyIntoStorage(source: File): File {
        val target = File(booksDir(), "${UUID.randomUUID()}.epub")
        source.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        return target
    }

    /**
     * Registers an EPUB that already lives in book storage. [remoteId]/[remoteSize]
     * are set when the file is also backed by Drive (e.g. just uploaded), so the
     * book is both local and remote.
     */
    suspend fun register(
        file: File,
        remoteId: String? = null,
        remoteSize: Long? = null,
    ): Long = finishImport(file, remoteId, remoteSize)

    private suspend fun finishImport(
        file: File,
        remoteId: String? = null,
        remoteSize: Long? = null,
    ): Long {
        val epub = parser.parse(file)
        val id = bookDao.insert(
            BookEntity(
                title = epub.title.ifBlank { file.nameWithoutExtension },
                author = epub.author.ifBlank { "Unknown" },
                addedAt = System.currentTimeMillis(),
                filePath = file.absolutePath,
                remoteId = remoteId,
                remoteSizeBytes = remoteSize,
            ),
        )
        // New books join the end of the hand-arranged order (ids are monotonic).
        bookDao.updateCustomOrder(id, id)

        epub.coverImageHref?.let { href ->
            val bytes = EpubResourceReader(file).use { it.read(href) }
            if (bytes != null) {
                val coverFile = File(coversDir(), "$id.${href.substringAfterLast('.', "img")}")
                coverFile.writeBytes(bytes)
                bookDao.updateCover(id, coverFile.absolutePath)
            }
        }
        return id
    }

    private fun booksDir() = File(context.filesDir, "books").apply { mkdirs() }
    private fun coversDir() = File(context.filesDir, "covers").apply { mkdirs() }
}
