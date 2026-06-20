package com.mipuble.domain.repository

import com.mipuble.domain.title.SeriesCatalog

/**
 * Supplies the canonical series catalog the importer matches against. The data
 * layer assembles it from the bundled base list plus a writable user overlay,
 * so names the user confirms in the review sheet persist and match next time.
 */
interface CatalogRepository {
    /** The current catalog (bundled base ∪ user overlay); implementations cache it. */
    suspend fun catalog(): SeriesCatalog

    /**
     * Appends an official name to the user overlay and rebuilds the cache, so it
     * becomes matchable immediately. No-op if it's already present.
     */
    suspend fun addSeries(name: String)
}
