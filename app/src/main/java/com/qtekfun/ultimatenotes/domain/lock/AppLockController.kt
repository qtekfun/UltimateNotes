// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.lock

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app lock state machine. The UI reports lifecycle events and settings changes; this decides
 * when the app is locked and unlocks it after a successful [LockPrompt]. Nothing here is logged.
 */
@Singleton
class AppLockController @Inject constructor(
    private val policy: LockPolicy,
    private val store: LockStore,
    private val source: LockConfigSource
) : AppLockState {
    private var config = source.current()
    private val state = MutableStateFlow(policy.shouldLock(config, store.backgroundedAt))
    private var authenticating = false

    override val locked: StateFlow<Boolean> = state.asStateFlow()

    /** The settings changed. Switching the lock off unlocks; switching it on keeps the session. */
    fun onConfigChanged(new: LockConfig) {
        config = new
        if (!new.enabled) {
            state.value = false
            store.backgroundedAt = null
        }
    }

    /** The app became visible: lock if it was away too long. */
    fun onEnterForeground() {
        if (authenticating) return
        if (!state.value && policy.shouldLock(config, store.backgroundedAt)) state.value = true
        // In the foreground and unlocked, a restored process must start locked: forget the time.
        if (!state.value) store.backgroundedAt = null
    }

    /** The app left the screen: note when, unless it is locked already. */
    fun onEnterBackground() {
        if (authenticating || !config.enabled || state.value) return
        store.backgroundedAt = policy.now()
    }

    /** Runs the prompt and unlocks on success; lifecycle events during it are ignored. */
    suspend fun authenticate(prompt: LockPrompt): AuthResult {
        if (authenticating) return AuthResult.Cancelled
        authenticating = true
        try {
            val result = prompt.authenticate()
            when (result) {
                AuthResult.Success -> unlock()

                // Nothing left to unlock with: the lock cannot protect anything and would only
                // lock the user out of their notes, so it is switched off.
                AuthResult.Unavailable -> {
                    source.disable()
                    unlock()
                }

                AuthResult.Cancelled -> Unit
            }
            return result
        } finally {
            authenticating = false
        }
    }

    private fun unlock() {
        state.value = false
        store.backgroundedAt = null
    }
}
