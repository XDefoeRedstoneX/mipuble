package com.mipuble.ui.reader

/**
 * Shared wiring between the ViewModel (which builds chapter URLs) and the
 * WebView (which intercepts them). Chapters are served from a virtual https
 * origin so that relative links to CSS/images inside a chapter resolve to the
 * same origin and get intercepted too — no unpacking the book to disk.
 */
object EpubWebViewBridge {
    /** Matches the well-known host used by androidx WebViewAssetLoader. */
    const val HOST = "appassets.androidplatform.net"
    const val PATH_PREFIX = "/epub/"
    const val BASE = "https://$HOST$PATH_PREFIX"

    fun mimeTypeFor(path: String): String = when (path.substringAfterLast('.').lowercase()) {
        "xhtml", "html", "htm" -> "text/html"
        "css" -> "text/css"
        "js" -> "application/javascript"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "ttf" -> "font/ttf"
        "otf" -> "font/otf"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        else -> "application/octet-stream"
    }
}
