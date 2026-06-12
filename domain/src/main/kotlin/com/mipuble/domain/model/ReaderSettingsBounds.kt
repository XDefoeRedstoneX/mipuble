package com.mipuble.domain.model

/**
 * The allowed ranges and step sizes for reader settings. Centralized (and pure)
 * so the stepping/clamping rules — including the deliberate 1% brightness
 * granularity — are unit-testable without touching Android.
 */
object ReaderSettingsBounds {

    const val BRIGHTNESS_MIN = 1
    const val BRIGHTNESS_MAX = 100
    /** The whole point of the feature: precise 1% increments. */
    const val BRIGHTNESS_STEP = 1

    const val FONT_MIN = 70
    const val FONT_MAX = 250
    const val FONT_STEP = 10

    const val LINE_MIN = 100
    const val LINE_MAX = 220
    const val LINE_STEP = 10

    /** [direction] is +1 or -1; returns the new clamped brightness percentage. */
    fun stepBrightness(current: Int, direction: Int): Int =
        (current + direction * BRIGHTNESS_STEP).coerceIn(BRIGHTNESS_MIN, BRIGHTNESS_MAX)

    fun clampBrightness(value: Int): Int = value.coerceIn(BRIGHTNESS_MIN, BRIGHTNESS_MAX)

    fun stepFont(current: Int, direction: Int): Int =
        (current + direction * FONT_STEP).coerceIn(FONT_MIN, FONT_MAX)

    fun stepLineSpacing(current: Int, direction: Int): Int =
        (current + direction * LINE_STEP).coerceIn(LINE_MIN, LINE_MAX)
}
