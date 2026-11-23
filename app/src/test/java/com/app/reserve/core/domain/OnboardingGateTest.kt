package com.app.reserve.core.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingGateTest {

    @Test
    fun `fresh install sees the welcome sheet`() {
        assertTrue(
            OnboardingGate.shouldShowWelcome(
                hasCompletedOnboarding = false,
                lastSmsScanAt = 0L,
                smsPermissionGranted = false,
                hasTransactions = false
            )
        )
    }

    @Test
    fun `completed flag suppresses the welcome sheet`() {
        assertFalse(
            OnboardingGate.shouldShowWelcome(
                hasCompletedOnboarding = true,
                lastSmsScanAt = 0L,
                smsPermissionGranted = false,
                hasTransactions = false
            )
        )
    }

    @Test
    fun `a previous SMS scan suppresses the welcome sheet`() {
        assertFalse(
            OnboardingGate.shouldShowWelcome(
                hasCompletedOnboarding = false,
                lastSmsScanAt = 1L,
                smsPermissionGranted = false,
                hasTransactions = false
            )
        )
    }

    @Test
    fun `already granted SMS permission suppresses the welcome sheet`() {
        assertFalse(
            OnboardingGate.shouldShowWelcome(
                hasCompletedOnboarding = false,
                lastSmsScanAt = 0L,
                smsPermissionGranted = true,
                hasTransactions = false
            )
        )
    }

    @Test
    fun `a manual-only ledger suppresses the welcome sheet`() {
        assertFalse(
            OnboardingGate.shouldShowWelcome(
                hasCompletedOnboarding = false,
                lastSmsScanAt = 0L,
                smsPermissionGranted = false,
                hasTransactions = true
            )
        )
    }
}
