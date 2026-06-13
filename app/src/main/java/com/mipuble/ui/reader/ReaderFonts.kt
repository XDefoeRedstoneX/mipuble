package com.mipuble.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.mipuble.domain.model.ReaderFont

/**
 * Resolves a [ReaderFont] to a Compose [FontFamily] for previewing in the UI.
 * Bundled faces load from assets/fonts; the generics map to platform families.
 * (The WebView reader applies fonts via CSS @font-face separately.)
 */
@Composable
fun rememberReaderFontFamily(font: ReaderFont): FontFamily {
    val assets = LocalContext.current.assets
    return remember(font) {
        when (font) {
            ReaderFont.BOOK -> FontFamily.Default
            ReaderFont.SERIF -> FontFamily.Serif
            ReaderFont.SANS_SERIF -> FontFamily.SansSerif
            ReaderFont.MONOSPACE -> FontFamily.Monospace
            else -> font.assetFileName()
                ?.let { FontFamily(Font(path = "fonts/$it", assetManager = assets)) }
                ?: FontFamily.Default
        }
    }
}
