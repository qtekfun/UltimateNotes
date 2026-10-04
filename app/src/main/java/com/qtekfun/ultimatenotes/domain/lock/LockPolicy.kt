// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.lock

import java.time.Clock
import javax.inject.Inject

/** Pure decision of whether returning to the app requires authenticating again. */
class LockPolicy @Inject constructor(private val clock: Clock) {
    fun now(): Long = clock.millis()

    /**
     * True when the app must be locked on coming back to the foreground. It is conservative
     * whenever the evidence is missing or odd: no timestamp (process death) or a clock that went
     * backwards (a manual change cannot be used to extend the grace period) both lock.
     */
    fun shouldLock(config: LockConfig, backgroundedAt: Long?): Boolean = when {
        !config.enabled -> false

        backgroundedAt == null -> true

        else -> {
            val away = clock.millis() - backgroundedAt
            away < 0 || away >= config.timeout.millis
        }
    }
}
