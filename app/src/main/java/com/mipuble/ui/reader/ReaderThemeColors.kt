package com.mipuble.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.mipuble.domain.model.PageTurnMode
import com.mipuble.ui.theme.Accent
import com.mipuble.ui.theme.AccentDark
import com.mipuble.ui.theme.Ink
import com.mipuble.ui.theme.InkBg
import com.mipuble.ui.theme.Paper
import com.mipuble.domain.model.ReaderFont
import com.mipuble.domain.model.ReaderPreferences
import com.mipuble.domain.model.ReaderTheme

/** Concrete page colors for each reading theme (the UI half of [ReaderTheme]). */
data class ReaderThemeColors(
    val background: Color,
    val text: Color,
    val link: Color,
) {
    companion object {
        fun of(theme: ReaderTheme): ReaderThemeColors = when (theme) {
            // The warm "Paper & Ink" palette. Brand tokens come from
            // ui/theme/Color.kt so a retune there carries into the page; the
            // remaining literals (SEPIA, page-only text tones) are reader-only.
            ReaderTheme.LIGHT -> ReaderThemeColors(Paper, Ink, Accent)
            ReaderTheme.SEPIA -> ReaderThemeColors(Color(0xFFEFE4CE), Color(0xFF4A3D2A), Color(0xFF7A542E))
            ReaderTheme.DARK -> ReaderThemeColors(InkBg, Color(0xFFD9D0C0), AccentDark)
            ReaderTheme.BLACK -> ReaderThemeColors(Color(0xFF000000), Color(0xFFB0B0B0), AccentDark)
        }
    }
}

private fun Color.toCssHex(): String {
    val argb = toArgb()
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return "#%02X%02X%02X".format(r, g, b)
}

/** Bundled typeface asset files (assets/fonts/...), keyed by [ReaderFont]. */
fun ReaderFont.assetFileName(): String? = when (this) {
    ReaderFont.LITERATA -> "Literata-Variable.ttf"
    ReaderFont.INTER -> "Inter-Variable.ttf"
    ReaderFont.ATKINSON_HYPERLEGIBLE -> "AtkinsonHyperlegible-Regular.ttf"
    else -> null
}

val ReaderFont.displayName: String
    get() = when (this) {
        ReaderFont.BOOK -> "Book default"
        ReaderFont.LITERATA -> "Literata"
        ReaderFont.INTER -> "Inter"
        ReaderFont.ATKINSON_HYPERLEGIBLE -> "Atkinson Hyperlegible"
        ReaderFont.SERIF -> "Serif"
        ReaderFont.SANS_SERIF -> "Sans serif"
        ReaderFont.MONOSPACE -> "Monospace"
    }

private fun fontCss(font: ReaderFont): String {
    val asset = font.assetFileName()
    return when {
        font == ReaderFont.BOOK -> ""
        asset != null -> """
            @font-face {
                font-family: 'mipuble-reader-font';
                src: url('${EpubWebViewBridge.FONT_BASE}$asset');
            }
            body, p, li, div, span, h1, h2, h3, h4, h5, h6 { font-family: 'mipuble-reader-font' !important; }
        """

        else -> {
            val generic = when (font) {
                ReaderFont.SERIF -> "serif"
                ReaderFont.MONOSPACE -> "monospace"
                else -> "sans-serif"
            }
            "body, p, li, div, span, h1, h2, h3, h4, h5, h6 { font-family: $generic !important; }"
        }
    }
}

/**
 * Lays the chapter out in screen-width CSS columns so horizontal swipes can
 * page through it; vertical overflow is what spills into the next column.
 */
private const val PAGED_CSS = """
    /* Clip vertical scrolling on the document, but let the body's columns
       overflow horizontally so the WebView's horizontal scroll range grows —
       that range is what the pager scrolls through. (A previous `overflow:
       hidden` on the body clipped the columns, leaving range 0 so every swipe
       jumped a chapter.) */
    html { height: 100vh !important; overflow-y: hidden !important; }
    body {
        height: 100vh !important;
        column-width: 100vw !important;
        column-gap: 0 !important;
        column-fill: auto !important;
        box-sizing: border-box !important;
    }
"""

/**
 * Builds the override stylesheet injected into every chapter. Font size is
 * handled separately via WebView.textZoom; this controls colors, typeface,
 * line spacing, page layout, comfortable margins, and image fit. `!important`
 * ensures it wins over the book's own CSS.
 */
fun readerOverrideCss(preferences: ReaderPreferences): String {
    val colors = ReaderThemeColors.of(preferences.theme)
    val bg = colors.background.toCssHex()
    val fg = colors.text.toCssHex()
    val link = colors.link.toCssHex()
    val lineHeight = preferences.lineSpacingPercent / 100f
    val paged = if (preferences.pageTurnMode == PageTurnMode.PAGED) PAGED_CSS else ""
    return """
        html, body { background-color: $bg !important; color: $fg !important; }
        body { line-height: $lineHeight !important; padding: 2vh 7% !important; margin: 0 !important; }
        p, li, div, span { color: $fg !important; }
        a { color: $link !important; }
        img, svg {
            max-width: 100% !important;
            max-height: 100vh !important;
            height: auto !important;
            object-fit: contain !important;
        }
        ${fontCss(preferences.font)}
        $paged
    """.trimIndent()
}
