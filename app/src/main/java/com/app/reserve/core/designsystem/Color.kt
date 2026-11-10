package com.app.reserve.core.designsystem

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Reserve's colour tokens. The values are repeated verbatim from the design
 * specification and must not be tinted, opacified or replaced with Material
 * defaults.
 */
data class ReserveColors(
    val isDark: Boolean,
    val canvas: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val border: Color,
    val primary: Color,
    val onPrimary: Color,
    val textHigh: Color,
    val textMed: Color,
    val textLow: Color,
    val income: Color,
    val expense: Color,
    val transfer: Color,
    val warning: Color
)

val ReserveLightColors = ReserveColors(
    isDark = false,
    canvas = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0F0F2),
    border = Color(0xFFE5E5E5),
    primary = Color(0xFF1E5142),
    onPrimary = Color(0xFFFFFFFF),
    textHigh = Color(0xFF1A1A1C),
    textMed = Color(0xFF4A4A4D),
    textLow = Color(0xFF7C7C80),
    income = Color(0xFF2D7D54),
    expense = Color(0xFFD14343),
    transfer = Color(0xFF4078C0),
    warning = Color(0xFFC27827)
)

val ReserveDarkColors = ReserveColors(
    isDark = true,
    canvas = Color(0xFF121214),
    surface = Color(0xFF1C1C1F),
    surfaceVariant = Color(0xFF2A2A2E),
    border = Color(0xFF2A2A2E),
    primary = Color(0xFF478A73),
    onPrimary = Color(0xFF000000),
    textHigh = Color(0xFFEDEDEF),
    textMed = Color(0xFFA1A1A5),
    textLow = Color(0xFF6B6B70),
    income = Color(0xFF4EAE7C),
    expense = Color(0xFFE57373),
    transfer = Color(0xFF6B9EE0),
    warning = Color(0xFFE5A85C)
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
