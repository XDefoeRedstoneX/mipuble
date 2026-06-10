package com.mipuble.data.epub

/**
 * Path helpers for resolving EPUB hrefs into zip entry names. EPUB hrefs are
 * relative to the file that references them (the OPF, or a chapter), so we
 * have to join and normalize them the way a browser resolves a relative URL.
 */
internal object EpubPaths {

    /** Directory portion of a zip entry path; "" for a root-level entry. */
    fun parentOf(path: String): String = path.substringBeforeLast('/', "")

    /**
     * Resolves [href] relative to [baseDir] and collapses "." and ".." segments.
     * An absolute href (leading "/") is resolved from the zip root.
     */
    fun resolve(baseDir: String, href: String): String {
        val combined = when {
            href.startsWith("/") -> href.removePrefix("/")
            baseDir.isEmpty() -> href
            else -> "$baseDir/$href"
        }
        return normalize(combined)
    }

    fun normalize(path: String): String {
        val out = ArrayDeque<String>()
        for (segment in path.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (out.isNotEmpty()) out.removeLast()
                else -> out.addLast(segment)
            }
        }
        return out.joinToString("/")
    }
}
