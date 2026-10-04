// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import com.qtekfun.ultimatenotes.domain.lock.LockCapability
import com.qtekfun.ultimatenotes.domain.lock.LockCapabilityChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Asks the platform what the lock can use: a biometric or the screen lock (Android 12+). */
class AndroidLockCapabilityChecker @Inject constructor(
    @ApplicationContext private val context: Context
) : LockCapabilityChecker {
    override fun capability(): LockCapability = fromStatus(
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
    )

    private fun fromStatus(status: Int) = when (status) {
        BiometricManager.BIOMETRIC_SUCCESS -> LockCapability.AVAILABLE
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> LockCapability.NOT_ENROLLED
        else -> LockCapability.UNSUPPORTED
    }
}
