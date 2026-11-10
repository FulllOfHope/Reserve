package com.app.reserve.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

@Composable
fun ReserveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) ReserveDarkColors else ReserveLightColors

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            primaryContainer = colors.surfaceVariant,
            onPrimaryContainer = colors.textHigh,
            secondary = colors.transfer,
            onSecondary = colors.onPrimary,
            background = colors.canvas,
            onBackground = colors.textHigh,
            surface = colors.surface,
            onSurface = colors.textHigh,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceVariant = colors.textMed,
            outline = colors.border,
            outlineVariant = colors.border,
            error = colors.expense,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            primaryContainer = colors.surfaceVariant,
            onPrimaryContainer = colors.textHigh,
            secondary = colors.transfer,
            onSecondary = Color.White,
            background = colors.canvas,
            onBackground = colors.textHigh,
            surface = colors.surface,
            onSurface = colors.textHigh,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceVariant = colors.textMed,
            outline = colors.border,
            outlineVariant = colors.border,
            error = colors.expense,
            onError = Color.White
        )
    }

    CompositionLocalProvider(LocalReserveColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ReserveTypography,
            content = content
        )
    }
}
