package com.mipuble.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// "Paper" — the default light scheme. Brand tokens (Color.kt) are mapped onto
// Material roles; hairline outlines are preferred over tonal elevation.
private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = AccentSoft,
    onPrimaryContainer = Accent,
    secondary = Ink2,
    onSecondary = Paper,
    tertiary = Accent,
    onTertiary = OnAccent,
    tertiaryContainer = AccentSoft,
    onTertiaryContainer = Accent,
    background = Paper,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = PaperCanvas,
    onSurfaceVariant = Ink2,
    outline = Line,
    outlineVariant = SurfaceOutline,
    scrim = Ink,
    error = Clay,
    onError = OnAccent,
)

// "Ink" — the warm-night dark scheme.
private val DarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = InkBg,
    primaryContainer = AccentSoftDark,
    onPrimaryContainer = AccentDark,
    secondary = InkText2,
    onSecondary = InkBg,
    tertiary = AccentDark,
    onTertiary = InkBg,
    tertiaryContainer = AccentSoftDark,
    onTertiaryContainer = AccentDark,
    background = InkBg,
    onBackground = InkText,
    surface = InkSurface,
    onSurface = InkText,
    surfaceVariant = InkSurface2,
    onSurfaceVariant = InkText2,
    outline = InkLine,
    outlineVariant = InkLine,
    scrim = InkBg,
    error = ClayDark,
    onError = InkBg,
)

@Composable
fun MipubleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Material You dynamic color is available on Android 12+, but the fixed
    // "Paper & Ink" brand palette is the default; leave the branch so a future
    // setting can opt back into dynamic color.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
