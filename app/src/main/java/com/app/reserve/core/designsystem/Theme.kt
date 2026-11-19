package com.app.reserve.core.designsystem

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun ReserveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    primaryColorHex: String = "#08C225",
    amountsHidden: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    val baseColors = if (darkTheme) ReserveDarkColors else ReserveLightColors
    val cleanHex = if (primaryColorHex.startsWith("#")) primaryColorHex else "#$primaryColorHex"
    val dynamicPrimary = runCatching { Color(android.graphics.Color.parseColor(cleanHex)) }.getOrNull() ?: baseColors.primaryDefault
    val colors = baseColors.copy(primaryDefault = dynamicPrimary)

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.primaryDefault,
            onPrimary = colors.textReverse,
            primaryContainer = colors.fillDark,
            onPrimaryContainer = colors.textBase,
            secondary = colors.textBase,
            onSecondary = colors.textReverse,
            background = colors.fillBase,
            onBackground = colors.textBase,
            surface = colors.surface,
            onSurface = colors.textBase,
            surfaceVariant = colors.fillLight,
            onSurfaceVariant = colors.textLight,
            outline = colors.strokeFaint,
            outlineVariant = colors.strokeFaint,
            error = colors.warningDefault,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = colors.primaryDefault,
            onPrimary = colors.textReverse,
            primaryContainer = colors.fillDark,
            onPrimaryContainer = colors.textBase,
            secondary = colors.textBase,
            onSecondary = Color.White,
            background = colors.fillBase,
            onBackground = colors.textBase,
            surface = colors.surface,
            onSurface = colors.textBase,
            surfaceVariant = colors.fillLight,
            onSurfaceVariant = colors.textLight,
            outline = colors.strokeFaint,
            outlineVariant = colors.strokeFaint,
            error = colors.warningDefault,
            onError = Color.White
        )
    }

    CompositionLocalProvider(
        LocalReserveColors provides colors,
        LocalAmountsHidden provides amountsHidden
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ReserveTypography,
            content = content
        )
    }
}
