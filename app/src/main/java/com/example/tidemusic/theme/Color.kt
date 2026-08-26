package com.example.tidemusic.theme

import androidx.compose.ui.graphics.Color

// ── AMOLED-grade dark palette ───────────────────────────────────────────────────
// True black background for OLED screens. Every surface is a subtle step up from
// pure black, giving depth without washing out. High-contrast white text on black.

/** App background — true black for OLED displays. */
val Background = Color(0xFF000000)
/** Cards, sheets, nav bar — very dark grey, just one notch above black. */
val Surface = Color(0xFF0D0D0D)
/** Elevated surface — player controls, dialogs, bottom sheets. */
val SurfaceElevated = Color(0xFF141414)
/** Outline / divider hairlines — subtle dark grey. */
val Outline = Color(0xFF1E1E1E)
/** Text primary — pure white for maximum contrast on black. */
val TextPrimary = Color(0xFFFFFFFF)
/** Text secondary — muted cool grey for metadata, timestamps. */
val TextSecondary = Color(0xFF808080)
/** Accent — cool electric blue, vivid against true black. */
val Accent = Color(0xFF0781FA)
/** Accent pressed/variant — deeper blue for pressed states. */
val AccentVariant = Color(0xFF0564C4)
/** Error / delete — vivid red, high contrast on black. */
val Error = Color(0xFFEF4444)

// ── Light palette (for light mode) ─────────────────────────────────────────────
/** App background — pure white throughout light mode. */
val LightBackground = Color(0xFFFFFFFF)
/** Cards, sheets, nav bar — slightly off-white for subtle elevation. */
val LightSurface = Color(0xFFFAFAFA)
/** Elevated surface — dialogs, bottom sheets. */
val LightSurfaceElevated = Color(0xFFF2F2F2)
val LightOutline = Color(0xFFE0E0E0)
val LightTextPrimary = Color(0xFF000000)
val LightTextSecondary = Color(0xFF6B6B6B)
val LightAccent = Color(0xFF0781FA)
val LightAccentVariant = Color(0xFF0564C4)

