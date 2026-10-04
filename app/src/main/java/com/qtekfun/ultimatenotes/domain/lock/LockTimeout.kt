// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.lock

private const val MINUTE_MS = 60_000L
private const val FIVE_MINUTES_MS = 300_000L
private const val FIFTEEN_MINUTES_MS = 900_000L

/** How long the app may stay in the background before it locks again (SPEC §8). */
enum class LockTimeout(val millis: Long) {
    IMMEDIATELY(0L),
    ONE_MINUTE(MINUTE_MS),
    FIVE_MINUTES(FIVE_MINUTES_MS),
    FIFTEEN_MINUTES(FIFTEEN_MINUTES_MS)
}
