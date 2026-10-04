// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.lock

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import com.qtekfun.ultimatenotes.domain.lock.LockCapability
import com.qtekfun.ultimatenotes.domain.lock.LockCapabilityChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Asks the platform what the lock can use. Before Android 11 `BIOMETRIC_WEAK or DEVICE_CREDENTIAL`
 * is not accepted by `canAuthenticate`, so the biometric and the screen lock are asked apart.
 */
class AndroidLockCapabilityChecker @Inject constructor(
    @ApplicationContext private val context: Context
) : LockCapabilityChecker {
    override fun capability(): LockCapability {
        val biometrics = BiometricManager.from(context)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            fromStatus(biometrics.canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL))
        } else {
            val keyguard = context.getSystemService(KeyguardManager::class.java)
            val biometricReady =
                biometrics.canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
            if (biometricReady || keyguard?.isDeviceSecure == true) {
                LockCapability.AVAILABLE
            } else {
                // A screen lock can always be set up in the system settings.
                LockCapability.NOT_ENROLLED
            }
        }
    }

    private fun fromStatus(status: Int) = when (status) {
        BiometricManager.BIOMETRIC_SUCCESS -> LockCapability.AVAILABLE
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> LockCapability.NOT_ENROLLED
        else -> LockCapability.UNSUPPORTED
    }
}
