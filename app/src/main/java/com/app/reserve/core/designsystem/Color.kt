package com.app.reserve.core.designsystem

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Reserve's colour tokens based on Ente Design System.
 */
data class ReserveColors(
    val isDark: Boolean,
    /** Page canvas. */
    val fillBase: Color,
    /** Cards, sheets and the floating navigation capsule. */
    val surface: Color,
    /** Subtle fills, search bar, inactive chips and the selected nav pill. */
    val fillLight: Color,
    /** Hairline dividers and switch tracks. */
    val fillDark: Color,
    /** Avatar containers and progress tracks. */
    val fillDarker: Color,
    val textBase: Color,
    val textLight: Color,
    val textLighter: Color,
    val textReverse: Color,
    val strokeFaint: Color,
    val primaryDefault: Color,
    /** Tint for active chips and selections. */
    val primaryContainer: Color,
    val warningDefault: Color
)

/** Warm Alabaster & Sovereign Gold. */
val ReserveLightColors = ReserveColors(
    isDark = false,
    fillBase = Color(0xFFFAF9F6),
    surface = Color(0xFFFFFFFF),
    fillLight = Color(0xFFF3F2EE),
    fillDark = Color(0xFFE8E6E0),
    fillDarker = Color(0xFFECEAE4),
    textBase = Color(0xFF18181A),
    textLight = Color(0xFF636366),
    textLighter = Color(0xFF8E8E93),
    textReverse = Color(0xFFFFFFFF),
    strokeFaint = Color(0xFFE8E6E0),
    primaryDefault = Color(0xFFC98A00),
    primaryContainer = Color(0xFFFFF6DF),
    warningDefault = Color(0xFFE53935)
)

/** Warm Obsidian & Luminous Amber. Never pure black. */
val ReserveDarkColors = ReserveColors(
    isDark = true,
    fillBase = Color(0xFF141413),
    surface = Color(0xFF1E1E1C),
    fillLight = Color(0xFF262624),
    fillDark = Color(0xFF2E2E2B),
    fillDarker = Color(0xFF2A2A27),
    textBase = Color(0xFFF5F5F3),
    textLight = Color(0xFFA8A8A4),
    textLighter = Color(0xFF787874),
    textReverse = Color(0xFF141413),
    strokeFaint = Color(0xFF2C2C29),
    primaryDefault = Color(0xFFF5B82E),
    primaryContainer = Color(0xFF2E2614),
    warningDefault = Color(0xFFEF5350)
)

/** Low-saturation category palette; readable on both light and dark surfaces. */
val CategoryPalette = listOf(
    Color(0xFF5C947F), // Mint
    Color(0xFF6B7F8C), // Slate
    Color(0xFF8C6B7C), // Mauve
    Color(0xFFA37861), // Clay
    Color(0xFFA39261), // Mustard
    Color(0xFF798C6B), // Sage
    Color(0xFF6B83A3), // Sky
    Color(0xFFA36B6B), // Coral
    Color(0xFF7C6BA3), // Lilac
    Color(0xFF948B7A)  // Sand
)

/** Hex strings stored in the database map back to the same palette values. */
fun categoryColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }
        .getOrDefault(CategoryPalette.first())

val LocalReserveColors = staticCompositionLocalOf { ReserveLightColors }
