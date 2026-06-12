package com.mipuble.domain.repository

import com.mipuble.domain.model.ReaderPreferences
import com.mipuble.domain.model.ReaderTheme
import kotlinx.coroutines.flow.Flow

/** Persisted reader settings; the data layer backs this with DataStore. */
interface ReaderPreferencesRepository {
    val preferences: Flow<ReaderPreferences>

    suspend fun setTheme(theme: ReaderTheme)
    suspend fun setFontScalePercent(value: Int)
    suspend fun setLineSpacingPercent(value: Int)
    suspend fun setBrightnessPercent(value: Int)
    suspend fun setFollowSystemBrightness(enabled: Boolean)
}
