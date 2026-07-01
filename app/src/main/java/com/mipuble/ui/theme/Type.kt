@file:OptIn(ExperimentalTextApi::class)

package com.mipuble.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mipuble.R

// ─────────────────────────────────────────────────────────────────────────────
// Two families carry the "Paper & Ink" chrome. Both ship as variable fonts under
// res/font/; the weight axis is pinned per style via FontVariation (API 26+).
//   • Literata — editorial serif for titles, sheet headers, and book titles.
//   • Hanken Grotesk — clean grotesque for UI, body, labels, and eyebrows.
// (The reader's WebView applies its own reading font separately, via CSS.)
// ─────────────────────────────────────────────────────────────────────────────

private fun literata(weight: FontWeight) =
    Font(R.font.literata, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

private fun hanken(weight: FontWeight) =
    Font(R.font.hanken_grotesk, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

val Literata = FontFamily(
    literata(FontWeight.Normal),
    literata(FontWeight.Medium),
)

val Hanken = FontFamily(
    hanken(FontWeight.Normal),
    hanken(FontWeight.Medium),
    hanken(FontWeight.SemiBold),
    hanken(FontWeight.Bold),
)

val Typography = Typography(
    // Literata — titles & bookish surfaces
    displaySmall = TextStyle(
        fontFamily = Literata,
        fontWeight = FontWeight.Medium,
        fontSize = 30.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Literata,
        fontWeight = FontWeight.Medium,
        fontSize = 23.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Literata,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Literata,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Literata,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    // Hanken Grotesk — UI, body, labels
    bodyLarge = TextStyle(
        fontFamily = Hanken,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Hanken,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Hanken,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Hanken,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Hanken,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Hanken,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.15.em,
    ),
)

/**
 * Uppercase section label ("eyebrow") — Hanken 11/600 with wide tracking. Pair
 * with `.uppercase()` on the text and tint with `ink3` (or `accent`) at the call
 * site. Mirrors [Typography.labelSmall] but named for intent.
 */
val eyebrow: TextStyle = Typography.labelSmall
