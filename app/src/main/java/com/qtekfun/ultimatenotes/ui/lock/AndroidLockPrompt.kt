// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.lock

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.domain.lock.AuthResult
import com.qtekfun.ultimatenotes.domain.lock.LockPrompt
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * [LockPrompt] over `androidx.biometric` (no Google Play Services). Needs a device to run.
 *
 * Android 11+ takes `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` in one prompt. On Android 8-10 that
 * combination is not supported, so the biometric prompt gets a "use screen lock" button that
 * opens the system confirm-credential screen (the KeyguardManager pattern from the library docs),
 * which is also used when no biometric is enrolled but the device has a secure screen lock.
 */
class AndroidLockPrompt(private val activity: FragmentActivity) : LockPrompt {
    override suspend fun authenticate(): AuthResult =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            biometric(BIOMETRIC_WEAK or DEVICE_CREDENTIAL, negativeText = null)
        } else {
            val biometricReady = BiometricManager.from(activity)
                .canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
            if (biometricReady) {
                val result = biometric(
                    BIOMETRIC_WEAK,
                    negativeText = activity.getString(R.string.lock_use_screen_lock)
                )
                if (result == AuthResult.Cancelled && usedFallbackButton) credential() else result
            } else {
                credential()
            }
        }

    private var usedFallbackButton = false

    private suspend fun biometric(authenticators: Int, negativeText: String?): AuthResult =
        suspendCancellableCoroutine { continuation ->
            usedFallbackButton = false
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    if (continuation.isActive) continuation.resume(AuthResult.Success)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    usedFallbackButton = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    if (continuation.isActive) continuation.resume(resultOfError(errorCode))
                }
            }
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                callback
            )
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.lock_prompt_title))
                .setAllowedAuthenticators(authenticators)
                .apply { negativeText?.let { setNegativeButtonText(it) } }
                .build()
            continuation.invokeOnCancellation { prompt.cancelAuthentication() }
            prompt.authenticate(info)
        }

    // Deprecated in the newest SDK, but it is the only credential screen on Android 8-10.
    @Suppress("DEPRECATION")
    private suspend fun credential(): AuthResult {
        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        val intent = keyguard
            ?.takeIf { it.isDeviceSecure }
            ?.createConfirmDeviceCredentialIntent(
                activity.getString(R.string.lock_prompt_title),
                null
            ) ?: return AuthResult.Unavailable
        return suspendCancellableCoroutine { continuation ->
            var launcher: ActivityResultLauncher<Intent>? = null
            launcher = activity.activityResultRegistry.register(
                "app-lock-credential-${UUID.randomUUID()}",
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                val outcome = if (result.resultCode == Activity.RESULT_OK) {
                    AuthResult.Success
                } else {
                    AuthResult.Cancelled
                }
                launcher?.unregister()
                if (continuation.isActive) continuation.resume(outcome)
            }
            continuation.invokeOnCancellation { launcher.unregister() }
            launcher.launch(intent)
        }
    }

    private fun resultOfError(code: Int): AuthResult = when (code) {
        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
        BiometricPrompt.ERROR_NO_BIOMETRICS,
        BiometricPrompt.ERROR_HW_NOT_PRESENT -> AuthResult.Unavailable

        else -> AuthResult.Cancelled
    }
}
