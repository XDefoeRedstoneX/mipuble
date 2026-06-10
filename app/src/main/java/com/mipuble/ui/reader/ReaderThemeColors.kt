package com.mipuble.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
            ReaderTheme.LIGHT -> ReaderThemeColors(Color(0xFFFFFFFF), Color(0xFF1A1A1A), Color(0xFF1B4D3E))
            ReaderTheme.SEPIA -> ReaderThemeColors(Color(0xFFF4ECD8), Color(0xFF5B4636), Color(0xFF7A542E))
            ReaderTheme.DARK -> ReaderThemeColors(Color(0xFF121212), Color(0xFFC9C9C9), Color(0xFF9FD8C2))
            ReaderTheme.BLACK -> ReaderThemeColors(Color(0xFF000000), Color(0xFFB0B0B0), Color(0xFF9FD8C2))
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

/**
 * Builds the override stylesheet injected into every chapter. Font size is
 * handled separately via WebView.textZoom; this controls colors, line spacing,
 * comfortable margins, and image fit. `!important` ensures it wins over the
 * book's own CSS.
 */
fun readerOverrideCss(preferences: ReaderPreferences): String {
    val colors = ReaderThemeColors.of(preferences.theme)
    val bg = colors.background.toCssHex()
    val fg = colors.text.toCssHex()
    val link = colors.link.toCssHex()
    val lineHeight = preferences.lineSpacingPercent / 100f
    return """
        html, body { background-color: $bg !important; color: $fg !important; }
        body { line-height: $lineHeight !important; padding: 0 6% !important; margin: 0 !important; }
        p, li, div, span { color: $fg !important; }
        a { color: $link !important; }
        img, svg { max-width: 100% !important; height: auto !important; }
    """.trimIndent()
}
