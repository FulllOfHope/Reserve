package com.app.reserve.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.app.reserve.R
import com.app.reserve.core.designsystem.LocalReserveColors

fun Context.findFragmentActivity(): FragmentActivity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is FragmentActivity) return context
        context = context.baseContext
    }
    return null
}

/** The only accessible screen while the app lock is armed. */
@Composable
fun AppLockScreen(onUnlocked: () -> Unit) {
    val colors = LocalReserveColors.current
    val context = LocalContext.current
    val activity = context.findFragmentActivity()
    var message by remember { mutableStateOf<String?>(null) }
    var starting by remember { mutableStateOf(false) }

    fun authenticate() {
        if (starting) return
        // Fail closed. A prompt that cannot be shown must never unlock the ledger.
        if (activity == null) {
            message = "Couldn't start biometric unlock. Reopen Reserve and try again."
            return
        }
        val manager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val availability = manager.canAuthenticate(authenticators)
        if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
            message = biometricAvailabilityMessage(availability)
            return
        }

        starting = true
        message = null
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                starting = false
                onUnlocked()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                starting = false
                message = biometricErrorMessage(errorCode)
            }

            override fun onAuthenticationFailed() {
                message = "That biometric wasn't recognised. Try again or use your device PIN."
            }
        }
        runCatching {
            val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context), callback)
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock Reserve")
                    .setSubtitle("Your ledger stays on this device")
                    .setAllowedAuthenticators(authenticators)
                    .build()
            )
        }.onFailure {
            starting = false
            message = "Couldn't start biometric unlock. Try again or use your device PIN."
        }
    }

    LaunchedEffect(Unit) { authenticate() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.fillBase)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_monochrome),
                contentDescription = "Reserve Logo",
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Reserve",
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textBase
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your data stays on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textLight,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = { authenticate() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = CircleShape,
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp, pressedElevation = 0.dp
                ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primaryDefault,
                    contentColor = colors.textReverse
                )
            ) {
                Text(
                    "Unlock",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 15.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                )
            }
            message?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textLight,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun biometricAvailabilityMessage(result: Int): String = when (result) {
    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
        "This device has no biometric hardware. Set a screen lock in device settings."
    BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
        "Biometric hardware is unavailable right now. Try again shortly."
    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
        "No fingerprint, face or screen lock is set up. Add one in device settings."
    BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
        "A security update is required before biometric unlock can be used."
    else -> "Biometric unlock is unavailable on this device."
}

private fun biometricErrorMessage(code: Int): String = when (code) {
    BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON ->
        "Unlock cancelled. Tap Unlock to try again."
    BiometricPrompt.ERROR_CANCELED ->
        "Unlock was interrupted. Tap Unlock to try again."
    BiometricPrompt.ERROR_TIMEOUT ->
        "Unlock timed out. Tap Unlock to try again."
    BiometricPrompt.ERROR_LOCKOUT ->
        "Too many attempts. Wait a moment, then use your device PIN."
    BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
        "Biometric unlock is locked. Use your device PIN."
    BiometricPrompt.ERROR_NO_BIOMETRICS ->
        "No biometrics are set up. Use your device PIN."
    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL ->
        "No device PIN or screen lock is set up."
    BiometricPrompt.ERROR_HW_NOT_PRESENT ->
        "This device has no biometric hardware."
    BiometricPrompt.ERROR_HW_UNAVAILABLE ->
        "Biometric hardware is unavailable. Try again shortly."
    BiometricPrompt.ERROR_VENDOR ->
        "This device couldn't start biometric unlock. Try again."
    else -> "Couldn't unlock. Try again or use your device PIN."
}
