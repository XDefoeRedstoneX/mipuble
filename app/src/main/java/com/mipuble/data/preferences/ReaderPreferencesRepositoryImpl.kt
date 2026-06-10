package com.mipuble.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mipuble.domain.model.ReaderPreferences
import com.mipuble.domain.model.ReaderSettingsBounds
import com.mipuble.domain.model.ReaderTheme
import com.mipuble.domain.repository.ReaderPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ReaderPreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ReaderPreferencesRepository {

    override val preferences: Flow<ReaderPreferences> = dataStore.data.map { prefs ->
        ReaderPreferences(
            theme = prefs[Keys.THEME]
                ?.let { runCatching { ReaderTheme.valueOf(it) }.getOrNull() }
                ?: ReaderTheme.LIGHT,
            fontScalePercent = prefs[Keys.FONT_SCALE] ?: 100,
            lineSpacingPercent = prefs[Keys.LINE_SPACING] ?: 150,
            brightnessPercent = prefs[Keys.BRIGHTNESS] ?: 50,
            followSystemBrightness = prefs[Keys.FOLLOW_SYSTEM] ?: true,
        )
    }

    override suspend fun setTheme(theme: ReaderTheme) {
        dataStore.edit { it[Keys.THEME] = theme.name }
    }

    override suspend fun setFontScalePercent(value: Int) {
        dataStore.edit {
            it[Keys.FONT_SCALE] = value.coerceIn(ReaderSettingsBounds.FONT_MIN, ReaderSettingsBounds.FONT_MAX)
        }
    }

    override suspend fun setLineSpacingPercent(value: Int) {
        dataStore.edit {
            it[Keys.LINE_SPACING] = value.coerceIn(ReaderSettingsBounds.LINE_MIN, ReaderSettingsBounds.LINE_MAX)
        }
    }

    override suspend fun setBrightnessPercent(value: Int) {
        dataStore.edit { it[Keys.BRIGHTNESS] = ReaderSettingsBounds.clampBrightness(value) }
    }

    override suspend fun setFollowSystemBrightness(enabled: Boolean) {
        dataStore.edit { it[Keys.FOLLOW_SYSTEM] = enabled }
    }

    private object Keys {
        val THEME = stringPreferencesKey("reader_theme")
        val FONT_SCALE = intPreferencesKey("reader_font_scale")
        val LINE_SPACING = intPreferencesKey("reader_line_spacing")
        val BRIGHTNESS = intPreferencesKey("reader_brightness")
        val FOLLOW_SYSTEM = booleanPreferencesKey("reader_follow_system_brightness")
    }
}
