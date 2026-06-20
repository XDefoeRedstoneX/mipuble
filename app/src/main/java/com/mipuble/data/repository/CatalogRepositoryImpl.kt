package com.mipuble.data.repository

import android.content.Context
import com.mipuble.domain.repository.CatalogRepository
import com.mipuble.domain.title.SeriesCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Builds the [SeriesCatalog] from two layers: the read-only base list bundled as
 * .txt files under the assets "catalog" folder, plus a writable overlay
 * (filesDir/catalog/user.txt) the user grows by confirming names in the review
 * sheet. The assembled catalog is cached and only rebuilt when the overlay changes.
 */
@Singleton
class CatalogRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : CatalogRepository {

    private val mutex = Mutex()
    @Volatile private var cached: SeriesCatalog? = null

    override suspend fun catalog(): SeriesCatalog {
        cached?.let { return it }
        return mutex.withLock {
            cached ?: build().also { cached = it }
        }
    }

    override suspend fun addSeries(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        mutex.withLock {
            val existing = readAllNames()
            if (existing.any { it.equals(clean, ignoreCase = true) }) return
            withContext(Dispatchers.IO) {
                overlayFile().apply { parentFile?.mkdirs() }.appendText(clean + "\n")
            }
            cached = SeriesCatalog(existing + clean)
        }
    }

    private suspend fun build(): SeriesCatalog =
        withContext(Dispatchers.IO) { SeriesCatalog(readAllNames()) }

    /** Bundled base names followed by user-overlay names. */
    private fun readAllNames(): List<String> = bundledNames() + overlayNames()

    private fun bundledNames(): List<String> =
        runCatching {
            (context.assets.list("catalog") ?: emptyArray())
                .filter { it.endsWith(".txt", ignoreCase = true) }
                .flatMap { file ->
                    context.assets.open("catalog/$file").bufferedReader().useLines { it.toList() }
                }
                .let(::parseLines)
        }.getOrDefault(emptyList())

    private fun overlayNames(): List<String> =
        overlayFile().takeIf { it.exists() }
            ?.runCatching { readLines() }?.getOrNull()
            ?.let(::parseLines)
            ?: emptyList()

    /** Drops blank lines and `#` comments, trimming the rest. */
    private fun parseLines(lines: List<String>): List<String> =
        lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }

    private fun overlayFile() = File(context.filesDir, "catalog/user.txt")
}
