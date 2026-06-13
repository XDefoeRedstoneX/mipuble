package com.mipuble.data.epub

import android.content.Context
import android.net.Uri
import com.mipuble.data.local.BookDao
import com.mipuble.data.local.BookEntity
import com.mipuble.domain.model.ImportOutcome
import com.mipuble.domain.title.TitleNormalizer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Brings an EPUB into the library: copies the file into app-private storage,
 * parses its metadata, cleans up its title, extracts the cover, and records a
 * [BookEntity]. Duplicate detection is two-pronged: exact bytes (content hash)
 * and a logical series|volume key, so a re-release of the same volume is caught
 * even when its bytes differ.
 */
@Singleton
class EpubImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parser: EpubParser,
    private val bookDao: BookDao,
) {

    /** Identity of an EPUB file: exact hash, cleaned title, and logical key. */
    data class Identity(
        val contentHash: String,
        val displayTitle: String,
        val author: String,
        val dedupKey: String?,
    )

    /** Computes a file's identity, preferring the EPUB's own title over the filename. */
    fun identify(file: File, fallbackName: String): Identity {
        val epub = runCatching { parser.parse(file) }.getOrNull()
        val rawTitle = epub?.title?.takeIf { it.isNotBlank() }
            ?: fallbackName.substringAfterLast('/').ifBlank { file.nameWithoutExtension }
        val normalized = TitleNormalizer.normalize(rawTitle)
        return Identity(
            contentHash = sha256(file),
            displayTitle = normalized.displayTitle,
            author = epub?.author?.takeIf { it.isNotBlank() } ?: "Unknown",
            dedupKey = normalized.dedupKey,
        )
    }

    /** True if an EPUB with these bytes, or this series|volume, is already present. */
    suspend fun isDuplicate(identity: Identity): Boolean =
        bookDao.findByHash(identity.contentHash) != null ||
            (identity.dedupKey != null && bookDao.findByDedupKey(identity.dedupKey) != null)

    /** Imports from a SAF content Uri chosen by the user; auto-skips duplicates. */
    suspend fun import(uriString: String): Result<ImportOutcome> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(uriString)
            val target = File(booksDir(), "${UUID.randomUUID()}.epub")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            } ?: error("Could not open $uriString")

            val identity = identify(target, fallbackName = uri.lastPathSegment.orEmpty())
            if (isDuplicate(identity)) {
                target.delete()
                ImportOutcome.Duplicate
            } else {
                ImportOutcome.Added(finishImport(target, identity))
            }
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
            finishImport(target, identify(target, fallbackName = assetName))
        }
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
    ): Long = finishImport(file, identify(file, fallbackName = file.name), remoteId, remoteSize)

    private suspend fun finishImport(
        file: File,
        identity: Identity,
        remoteId: String? = null,
        remoteSize: Long? = null,
    ): Long {
        val epub = runCatching { parser.parse(file) }.getOrNull()
        val id = bookDao.insert(
            BookEntity(
                title = identity.displayTitle.ifBlank { file.nameWithoutExtension },
                author = identity.author,
                addedAt = System.currentTimeMillis(),
                filePath = file.absolutePath,
                remoteId = remoteId,
                remoteSizeBytes = remoteSize,
                contentHash = identity.contentHash,
                dedupKey = identity.dedupKey,
            ),
        )
        // New books join the end of the hand-arranged order (ids are monotonic).
        bookDao.updateCustomOrder(id, id)

        epub?.coverImageHref?.let { href ->
            val bytes = EpubResourceReader(file).use { it.read(href) }
            if (bytes != null) {
                val coverFile = File(coversDir(), "$id.${href.substringAfterLast('.', "img")}")
                coverFile.writeBytes(bytes)
                bookDao.updateCover(id, coverFile.absolutePath)
            }
        }
        return id
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun booksDir() = File(context.filesDir, "books").apply { mkdirs() }
    private fun coversDir() = File(context.filesDir, "covers").apply { mkdirs() }
}
