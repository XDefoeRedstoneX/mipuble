package com.mipuble.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderSettingsBoundsTest {

    @Test
    fun `brightness steps in exact 1 percent increments`() {
        assertEquals(51, ReaderSettingsBounds.stepBrightness(50, +1))
        assertEquals(49, ReaderSettingsBounds.stepBrightness(50, -1))
    }

    @Test
    fun `brightness can be dimmed to 1 percent but no lower`() {
        assertEquals(1, ReaderSettingsBounds.stepBrightness(2, -1))
        assertEquals(1, ReaderSettingsBounds.stepBrightness(1, -1))
    }

    @Test
    fun `brightness is capped at 100 percent`() {
        assertEquals(100, ReaderSettingsBounds.stepBrightness(100, +1))
    }

    @Test
    fun `clampBrightness keeps values within range`() {
        assertEquals(1, ReaderSettingsBounds.clampBrightness(0))
        assertEquals(1, ReaderSettingsBounds.clampBrightness(-30))
        assertEquals(100, ReaderSettingsBounds.clampBrightness(250))
        assertEquals(37, ReaderSettingsBounds.clampBrightness(37))
    }

    @Test
    fun `font scales in 10 percent steps within range`() {
        assertEquals(110, ReaderSettingsBounds.stepFont(100, +1))
        assertEquals(ReaderSettingsBounds.FONT_MAX, ReaderSettingsBounds.stepFont(ReaderSettingsBounds.FONT_MAX, +1))
        assertEquals(ReaderSettingsBounds.FONT_MIN, ReaderSettingsBounds.stepFont(ReaderSettingsBounds.FONT_MIN, -1))
    }

    @Test
    fun `line spacing steps within range`() {
        assertEquals(160, ReaderSettingsBounds.stepLineSpacing(150, +1))
        assertEquals(ReaderSettingsBounds.LINE_MAX, ReaderSettingsBounds.stepLineSpacing(ReaderSettingsBounds.LINE_MAX, +1))
        assertEquals(ReaderSettingsBounds.LINE_MIN, ReaderSettingsBounds.stepLineSpacing(ReaderSettingsBounds.LINE_MIN, -1))
    }
}
