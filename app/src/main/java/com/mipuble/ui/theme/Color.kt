package com.mipuble.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// "Paper & Ink" brand palette. A fixed, warm, literary set of tones replaces
// stock Material You dynamic color (which becomes opt-in in Theme.kt). Tokens
// are named by role; the color scheme in Theme.kt maps them onto Material slots.
// ─────────────────────────────────────────────────────────────────────────────

// Light theme — "Paper"
val Paper = Color(0xFFF5EFE4)          // app background / screen
val PaperCanvas = Color(0xFFE4DAC8)    // deepest canvas (behind sheets, scrim base)
val Surface = Color(0xFFFCF8F0)        // cards, sheets, raised rows
val SurfaceOutline = Color(0xFFEADFCC) // hairline on surfaces
val Line = Color(0xFFE0D6C4)           // dividers, chip borders
val Ink = Color(0xFF241F19)            // primary text
val Ink2 = Color(0xFF6E6456)           // secondary text
val Ink3 = Color(0xFF9C8F7B)           // tertiary / meta / eyebrows
val Accent = Color(0xFF3E6B54)         // primary actions, active state, progress, FAB
val OnAccent = Color(0xFFF5EFE4)       // text/icon on accent
val AccentSoft = Color(0xFFE6EDE6)     // accent tint fills
val Clay = Color(0xFFA8493A)           // error

// Dark theme — "Ink" (warm night)
val InkBg = Color(0xFF16130E)          // screen background
val InkSurface = Color(0xFF201B15)     // cards, rows
val InkSurface2 = Color(0xFF2D2820)    // stepper/control fills
val InkLine = Color(0xFF2E2820)        // dividers
val InkText = Color(0xFFE9E1D3)        // primary text
val InkText2 = Color(0xFFA99D8B)       // secondary
val InkText3 = Color(0xFF776E60)       // tertiary / meta
val AccentDark = Color(0xFF93C2A2)     // accent on dark (lighter sage)
val AccentSoftDark = Color(0xFF2A352C) // accent tint fill on dark
val SheetBg = Color(0xFF221D16)        // reader settings sheet
val ClayDark = Color(0xFFCF9A8F)       // error on dark
