package com.app.reserve.core.designsystem

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Ente Colour Tokens for Light and Dark themes.
 */
data class ReserveColors(
    val isDark: Boolean,
    /** Page background (#FAFAFA light / #161616 dark). */
    val fillBase: Color,
    /** Raised surface (#FFFFFF / #212121). */
    val surface: Color,
    /** Subtle fill (#FFFFFF / #212121). */
    val fillLight: Color,
    /** Hairline stroke / container (#F5F5F5 / #2C2C2E). */
    val fillDark: Color,
    /** Track / inactive fill (#EBEBEB / #1C1C1E). */
    val fillDarker: Color,
    val textBase: Color,
    /** Secondary text (#666666 / #B3B3B3). */
    val textLight: Color,
    /** Tertiary text (#8E8E93 / #7F7F7F). */
    val textLighter: Color,
    val textReverse: Color,
    val strokeFaint: Color,
    val primaryDefault: Color,
    val warningDefault: Color
)

val ReserveLightColors = ReserveColors(
    isDark = false,
    fillBase = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    fillLight = Color(0xFFFFFFFF),
    fillDark = Color(0xFFF5F5F5),
    fillDarker = Color(0xFFEBEBEB),
    textBase = Color(0xFF1C1C1E),
    textLight = Color(0xFF666666),
    textLighter = Color(0xFF8E8E93),
    textReverse = Color(0xFFFFFFFF),
    strokeFaint = Color(0xFFEBEBEB),
    primaryDefault = Color(0xFF08C225),
    warningDefault = Color(0xFFF63A3A)
)

val ReserveDarkColors = ReserveColors(
    isDark = true,
    fillBase = Color(0xFF161616),
    surface = Color(0xFF212121),
    fillLight = Color(0xFF212121),
    fillDark = Color(0xFF2C2C2E),
    fillDarker = Color(0xFF1C1C1E),
    textBase = Color(0xFFFFFFFF),
    textLight = Color(0xFFB3B3B3),
    textLighter = Color(0xFF7F7F7F),
    textReverse = Color(0xFF000000),
    strokeFaint = Color(0xFF2A2A2A),
    primaryDefault = Color(0xFF069D1E),
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
