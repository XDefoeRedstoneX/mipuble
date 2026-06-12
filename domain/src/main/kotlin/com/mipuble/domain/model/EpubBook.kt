package com.mipuble.domain.model

/**
 * The parsed shape of an EPUB, reduced to what the reader needs: the chapters
 * in reading order and an optional cover. Paths are *resolved* entry names
 * within the EPUB zip (e.g. "OEBPS/Text/chap1.xhtml"), ready to read directly.
 */
data class EpubBook(
    val title: String,
    val author: String,
    /** Spine items resolved to zip entry paths, in reading order. */
    val spineHrefs: List<String>,
    /** Cover image resolved to a zip entry path, or null if the book has none. */
    val coverImageHref: String?,
)
