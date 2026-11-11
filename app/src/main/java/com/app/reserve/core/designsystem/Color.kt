package com.app.reserve.core.designsystem

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Reserve's colour tokens based on Ente Design System.
 */
data class ReserveColors(
    val isDark: Boolean,
    val fillBase: Color,
    val fillLight: Color,
    val fillDark: Color,
    val fillDarker: Color,
    val textBase: Color,
    val textLight: Color,
    val textLighter: Color,
    val textReverse: Color,
    val strokeFaint: Color,
    val primaryDefault: Color,
    val warningDefault: Color
)

val ReserveLightColors = ReserveColors(
    isDark = false,
    fillBase = Color(0xFFFFFFFF),       // Pure White
    fillLight = Color(0xFFF5F5F5),      // Light Grey
    fillDark = Color(0xFFEDEDED),
    fillDarker = Color(0xFFD4D4D8),
    textBase = Color(0xFF1C1C1E),       // Heavy text
    textLight = Color(0xFF8E8E93),      // Cap values / dates
    textLighter = Color(0xFF8E8E93), 
    textReverse = Color(0xFFFFFFFF),
    strokeFaint = Color(0xFFEDEDED),    // Fine partition hairline
    primaryDefault = Color(0xFF0FB70F),  // Vibrant Ente Green
    warningDefault = Color(0xFFD03434)   // Critical Red
)

val ReserveDarkColors = ReserveColors(
    isDark = true,
    fillBase = Color(0xFF000000),       // Pure Black
    fillLight = Color(0xFF151515),      // Dark Surface
    fillDark = Color(0xFF292929),
    fillDarker = Color(0xFF3E3E3E),
    textBase = Color(0xFFFFFFFF),       // Pure White
    textLight = Color(0xFFA1A1AA),
    textLighter = Color(0xFFA1A1AA),
    textReverse = Color(0xFF000000),
    strokeFaint = Color(0xFF292929),
    primaryDefault = Color(0xFF067018),  // Ente Green
    warningDefault = Color(0xFFDD3434)
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
