package com.app.reserve.core.designsystem

import androidx.compose.runtime.staticCompositionLocalOf
import com.app.reserve.core.domain.Money

/**
 * Privacy mode replaces every visible amount with [HiddenAmount] so the app can
 * be used in public. It never changes stored data, and the amount being typed on
 * the add screen is deliberately left unmasked.
 */
const val HiddenAmount = "••••••"

/** True while the user has chosen to hide amounts across the UI. */
val LocalAmountsHidden = staticCompositionLocalOf { false }

/**
 * Formats [amount] for display, replacing it with [HiddenAmount] when privacy
 * mode is on. Plain (non-composable) callers pass the flag they read from
 * [LocalAmountsHidden] at the composable boundary.
 */
fun formatMoney(
    amount: Long,
    hidden: Boolean,
    showPlus: Boolean = false
): String = if (hidden) HiddenAmount else Money.format(amount, showPlus = showPlus)
