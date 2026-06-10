package com.mipuble.domain.model

/** Background/text palette options for the reader. Concrete colors live in the UI layer. */
enum class ReaderTheme {
    LIGHT,
    SEPIA,
    DARK,
    BLACK,
}

/**
 * User reading preferences, persisted across sessions. Brightness is expressed
 * as a whole percentage so the UI can offer exact ±1% control that overrides
 * the system default while reading.
 */
data class ReaderPreferences(
    val theme: ReaderTheme = ReaderTheme.LIGHT,
    val fontScalePercent: Int = 100,
    val lineSpacingPercent: Int = 150,
    val brightnessPercent: Int = 50,
    /** When true, the reader leaves screen brightness to the system. */
    val followSystemBrightness: Boolean = true,
)
