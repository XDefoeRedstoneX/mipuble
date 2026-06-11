package com.mipuble.data.local

import com.mipuble.data.epub.EpubImporter
import javax.inject.Inject

/**
 * Populates a freshly created database: demo categories, the metadata-only
 * demo series (which exercises natural sorting and foreshadows the
 * not-yet-downloaded books of Phase 5), plus one real, openable sample EPUB
 * imported from assets.
 */
class DatabaseSeeder @Inject constructor(
    private val bookDao: BookDao,
    private val categoryDao: CategoryDao,
    private val importer: EpubImporter,
) {
    suspend fun seed() {
        val categoryIds = SeedData.categories.map { categoryDao.insert(it) }
        bookDao.insertAll(SeedData.books(categoryIds))
        // Best-effort: a missing/invalid sample shouldn't break first launch.
        importer.importFromAsset(SAMPLE_ASSET)
    }

    private companion object {
        const val SAMPLE_ASSET = "sample.epub"
    }
}
