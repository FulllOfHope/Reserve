package com.app.reserve.core.domain

/**
 * Pure guard for the first-run welcome sheet.
 *
 * A fresh install is the *only* case that should see onboarding. Every existing
 * install is considered onboarded when any one of these signals is present:
 * the flag itself, a previous SMS scan, already-granted SMS permission, or any
 * transaction in the ledger (manual-only users must not get a surprise sheet).
 */
object OnboardingGate {

    fun shouldShowWelcome(
        hasCompletedOnboarding: Boolean,
        lastSmsScanAt: Long,
        smsPermissionGranted: Boolean,
        hasTransactions: Boolean
    ): Boolean = !hasCompletedOnboarding &&
        lastSmsScanAt <= 0L &&
        !smsPermissionGranted &&
        !hasTransactions
}
