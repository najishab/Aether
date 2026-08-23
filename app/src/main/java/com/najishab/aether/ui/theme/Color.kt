package com.najishab.aether.ui.theme

import androidx.compose.ui.graphics.Color

// "Dark Tech" palette: deep navy background, electric-blue brand colour, and
// a distinct neon red/green pair so the connected/disconnected state reads at
// a glance - the power button, the status text and the card's glowing edge
// all switch together instead of sitting on the same static blue in both
// states.
val Navy900 = Color(0xFF070B17)
val Navy800 = Color(0xFF111A32)
val Navy700 = Color(0xFF16264A)
val Navy600 = Color(0xFF223357)

val AetherBlue = Color(0xFF4D8DFF)
val AetherCyan = Color(0xFF38BDF8)

/** Connected / success state - status text, power button and card glow. */
val AetherSuccess = Color(0xFF22C55E)
val AetherSuccessGradientStart = Color(0xFF16C784)
val AetherSuccessGradientEnd = Color(0xFF38E08B)

/** Disconnected / error state - status text, power button and card glow. */
val AetherDanger = Color(0xFFFF4D67)
val AetherDangerGradientEnd = Color(0xFFFF6B81)
/** Alias so MaterialTheme's `error` slot reads the same coral red. */
val AetherError = AetherDanger

val OnDark = Color(0xFFF1F5FF)
val OnDarkMuted = Color(0xFF8995B3)

// ---- Brand tokens for the unified connection card (1.2.6) ----
//
// The card is pinned to these instead of MaterialTheme, because Material You
// repaints every themed surface from the user's wallpaper on Android 12+ and
// that turned the connection card into a colour that was no longer Aether.

/** Glass card surface: a slate a shade lighter than the navy backdrop. */
val CardSurfaceTop = Color(0xF0111A32)
val CardSurfaceBottom = Color(0xF6070B17)
/** Every sub-container inside the card (IP pill, speed strip, protocol strip). */
val CardSubSurface = Color(0xFF16264A)

val CardTextPrimary = Color(0xFFF1F5FF)
val CardTextMuted = Color(0xFF8995B3)
val CardTextDim = Color(0xFF5E6B84)
