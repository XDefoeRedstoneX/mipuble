package com.mipuble.data.remote

import android.content.Context
import com.mipuble.data.epub.EpubParser
import com.mipuble.data.epub.EpubResourceReader
import com.mipuble.domain.model.RemoteBook
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

/**
 * An offline stand-in for a real cloud library, so download-on-demand and
 * eviction can be demoed and tested without credentials or a network. Every
 * "remote" book is backed by the bundled sample EPUB; downloads copy it with a
 * simulated, chunked progress curve.
 */
@Singleton
class FakeRemoteLibrarySource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parser: EpubParser,
) : RemoteLibrarySource {

    // A series, specifically to show natural sorting working on remote books too.
    // Mutable so simulated uploads appear in the "folder" on the next sync.
    private val catalog = buildList {
        repeat(8) { index ->
            add(
                RemoteBook(
                    remoteId = "fake-deep-archive-${index + 1}",
                    title = "Deep Archive, Vol. ${index + 1}",
                    author = "Cloud Author",
                    sizeBytes = 4_200_000L + index * 180_000L,
                ),
            )
        }
        add(RemoteBook("fake-tidal", "Tidal Mechanics", "R. Okonkwo", 6_800_000L))
        add(RemoteBook("fake-lanterns", "A Field of Lanterns", "Yuki Sato", 5_100_000L))
    }.toMutableList()

    override suspend fun isAvailable(): Boolean = true

    override suspend fun listBooks(): List<RemoteBook> = catalog.toList()

    override suspend fun uploadBook(
        file: File,
        displayName: String,
        onProgress: (Float) -> Unit,
    ): RemoteBook {
        // Simulate a chunked upload so the progress UI animates.
        for (i in 1..10) {
            onProgress(i / 10f)
            delay(40)
        }
        val name = displayName.removeSuffix(".epub")
        val book = RemoteBook(
            remoteId = "fake-upload-${java.util.UUID.randomUUID()}",
            title = name,
            author = "",
            sizeBytes = file.length(),
        )
        catalog.add(book)
        return book
    }

    override suspend fun trashBook(remoteId: String) {
        catalog.removeAll { it.remoteId == remoteId }
    }

    override suspend fun fetchCover(remoteId: String): ByteArray? {
        val sample = cachedSample() ?: return null
        val epub = runCatching { parser.parse(sample) }.getOrNull() ?: return null
        val href = epub.coverImageHref ?: return null
        return EpubResourceReader(sample).use { it.read(href) }
    }

    override suspend fun download(remoteId: String, target: File, onProgress: (Float) -> Unit) {
        val sample = cachedSample() ?: error("Sample asset unavailable")
        val bytes = sample.readBytes()

        // Simulate a chunked transfer so the UI progress ring actually animates.
        val chunks = 10
        target.outputStream().use { out ->
            for (i in 1..chunks) {
                val end = (bytes.size * i / chunks)
                val start = (bytes.size * (i - 1) / chunks)
                out.write(bytes, start, end - start)
                onProgress(i / chunks.toFloat())
                delay(60)
            }
        }
    }

    /** Copies the bundled sample to cache once so it can be parsed as a File. */
    private fun cachedSample(): File? {
        val cached = File(context.cacheDir, "fake_remote_sample.epub")
        if (!cached.exists()) {
            runCatching {
                context.assets.open(SAMPLE_ASSET).use { input ->
                    cached.outputStream().use { input.copyTo(it) }
                }
            }.onFailure { return null }
        }
        return cached.takeIf { it.exists() }
    }

    private companion object {
        const val SAMPLE_ASSET = "sample.epub"
    }
}
