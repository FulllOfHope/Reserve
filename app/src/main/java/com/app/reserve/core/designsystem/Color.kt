package com.app.reserve.core.designsystem

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Reserve's colour tokens based on the Ente Design System.
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

/** Crisp Gallery & Vivid Emerald. */
val ReserveLightColors = ReserveColors(
    isDark = false,
    fillBase = Color(0xFFF8F9FA),
    surface = Color(0xFFFFFFFF),
    fillLight = Color(0xFFF1F3F5),
    fillDark = Color(0xFFE9ECEF),
    fillDarker = Color(0xFFE2E6EA),
    textBase = Color(0xFF111315),
    textLight = Color(0xFF5C626A),
    textLighter = Color(0xFF8A9099),
    textReverse = Color(0xFFFFFFFF),
    strokeFaint = Color(0xFFE6E8EC),
    primaryDefault = Color(0xFF00B843),
    primaryContainer = Color(0xFFE6F9ED),
    warningDefault = Color(0xFFF03E3E)
)

/** Deep Carbon & Neon Mint. Never pure black. */
val ReserveDarkColors = ReserveColors(
    isDark = true,
    fillBase = Color(0xFF121316),
    surface = Color(0xFF1B1D21),
    fillLight = Color(0xFF23262B),
    fillDark = Color(0xFF2C3036),
    fillDarker = Color(0xFF272A30),
    textBase = Color(0xFFF8F9FA),
    textLight = Color(0xFFA6ACB5),
    textLighter = Color(0xFF727882),
    textReverse = Color(0xFF0B140E),
    strokeFaint = Color(0xFF282B30),
    primaryDefault = Color(0xFF1CE865),
    primaryContainer = Color(0xFF132B1C),
    warningDefault = Color(0xFFFF5252)
)

/** Accent swatches shown in Settings → Appearance. */
data class AccentSwatch(
    val name: String,
    val lightHex: String,
    val darkHex: String
)

/** Ten vibrant accents with a luminous sibling for dark mode. */
val AccentSwatches = listOf(
    AccentSwatch("Emerald", "#00B843", "#1CE865"),
    AccentSwatch("Electric Blue", "#2563EB", "#60A5FA"),
    AccentSwatch("Royal Violet", "#7C3AED", "#A78BFA"),
    AccentSwatch("Crimson Rose", "#E11D48", "#FB7185"),
    AccentSwatch("Tangerine", "#EA580C", "#FB923C"),
    AccentSwatch("Solar Amber", "#D97706", "#FBBF24"),
    AccentSwatch("Ocean Teal", "#0D9488", "#2DD4BF"),
    AccentSwatch("Deep Indigo", "#4F46E5", "#818CF8"),
    AccentSwatch("Magenta", "#C026D3", "#E879F9"),
    AccentSwatch("Obsidian Monochrome", "#18181B", "#F4F4F5")
)

private val accentByHex = AccentSwatches.associateBy { it.lightHex.uppercase() }

/** Old presets are migrated to their vibrant replacements without touching stored settings. */
private val legacyAccentHex = mapOf(
    "#C98A00" to "#00B843", // Sovereign Gold → Emerald
    "#F5B82E" to "#1CE865", // Luminous Amber → Neon Mint
    "#08C225" to "#00B843", // Ente Green → Emerald
    "#069D1E" to "#00B843",
    "#1D63DC" to "#2563EB", // Deep Cobalt → Electric Blue
    "#D95D39" to "#EA580C"  // Terracotta Coral → Tangerine
)

/** Normalises a stored hex to one of the ten supported light accents. */
fun normalizedAccentHex(hex: String): String {
    val clean = if (hex.startsWith("#")) hex else "#$hex"
    val upper = clean.uppercase()
    return legacyAccentHex[upper] ?: accentByHex[upper]?.lightHex ?: clean
}

/** Resolves the stored accent to its light or dark variant. */
fun resolveAccentHex(hex: String, darkTheme: Boolean): String {
    val normalized = normalizedAccentHex(hex)
    return if (darkTheme) accentByHex[normalized.uppercase()]?.darkHex ?: normalized else normalized
}

/** Saturated category palette; readable on both light and dark surfaces. */
val CategoryPalette = listOf(
    Color(0xFF10B981), // Emerald
    Color(0xFF3B82F6), // Azure
    Color(0xFF8B5CF6), // Violet
    Color(0xFFF43F5E), // Rose
    Color(0xFFF59E0B), // Amber
    Color(0xFF06B6D4), // Cyan
    Color(0xFF6366F1), // Indigo
    Color(0xFFFB7185), // Coral
    Color(0xFF14B8A6), // Teal
    Color(0xFF64748B)  // Slate
)

/** Existing seeded categories are upgraded visually without rewriting the database. */
private val legacyCategoryColors = mapOf(
    "#5C947F" to "#10B981",
    "#6B7F8C" to "#64748B",
    "#8C6B7C" to "#F43F5E",
    "#A37861" to "#FB7185",
    "#A39261" to "#F59E0B",
    "#798C6B" to "#14B8A6",
    "#6B83A3" to "#3B82F6",
    "#A36B6B" to "#F43F5E",
    "#7C6BA3" to "#8B5CF6",
    "#948B7A" to "#64748B"
)

/** Hex strings stored in the database map back to the vibrant palette. */
fun categoryColor(hex: String): Color {
    val normalized = legacyCategoryColors[hex.uppercase()] ?: hex
    return runCatching { Color(android.graphics.Color.parseColor(normalized)) }
        .getOrDefault(CategoryPalette.first())
}

val LocalReserveColors = staticCompositionLocalOf { ReserveLightColors }
