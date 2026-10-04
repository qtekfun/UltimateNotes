// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.lock

/** What the user chose about the app lock; off by default. */
data class LockConfig(
    val enabled: Boolean = false,
    val timeout: LockTimeout = LockTimeout.IMMEDIATELY
)

/** Where the controller reads the lock settings from, and how it switches the lock off. */
interface LockConfigSource {
    /** The settings right now, readable synchronously so the app starts locked, not flashing. */
    fun current(): LockConfig

    /** Turns the lock off, for when the device no longer has any screen lock to unlock with. */
    fun disable()
}
