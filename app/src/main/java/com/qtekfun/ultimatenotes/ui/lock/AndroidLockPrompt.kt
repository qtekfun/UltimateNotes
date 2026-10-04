// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.lock

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.domain.lock.AuthResult
import com.qtekfun.ultimatenotes.domain.lock.LockPrompt
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * [LockPrompt] over `androidx.biometric` (no Google Play Services). Needs a device to run.
 *
 * The app needs Android 12+, so `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` is always accepted in one
 * prompt: the screen lock is offered by the system prompt itself.
 */
class AndroidLockPrompt(private val activity: FragmentActivity) : LockPrompt {
    override suspend fun authenticate(): AuthResult = suspendCancellableCoroutine { continuation ->
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (continuation.isActive) continuation.resume(AuthResult.Success)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (continuation.isActive) continuation.resume(resultOfError(errorCode))
            }
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.lock_prompt_title))
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()
        continuation.invokeOnCancellation { prompt.cancelAuthentication() }
        prompt.authenticate(info)
    }

    private fun resultOfError(code: Int): AuthResult = when (code) {
        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
        BiometricPrompt.ERROR_NO_BIOMETRICS,
        BiometricPrompt.ERROR_HW_NOT_PRESENT -> AuthResult.Unavailable

        else -> AuthResult.Cancelled
    }
}
