// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.lock

/** Whether the device can authenticate the user at all. */
enum class LockCapability {
    /** A biometric or the device PIN/pattern/password can be used. */
    AVAILABLE,

    /** The device can authenticate, but the user has not set a screen lock or biometric up. */
    NOT_ENROLLED,

    /** The device cannot authenticate (no hardware and no secure screen lock). */
    UNSUPPORTED
}

/** Reports [LockCapability]; needs no activity. */
fun interface LockCapabilityChecker {
    fun capability(): LockCapability
}

sealed interface AuthResult {
    data object Success : AuthResult

    /** The user dismissed the prompt, or gave up; stay locked. */
    data object Cancelled : AuthResult

    /** Authentication cannot work right now (e.g. all credentials were removed). */
    data object Unavailable : AuthResult
}

/** The system prompt, bound to an activity. Hardware paths live behind this interface. */
fun interface LockPrompt {
    suspend fun authenticate(): AuthResult
}
