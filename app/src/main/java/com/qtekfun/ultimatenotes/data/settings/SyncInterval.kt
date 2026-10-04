// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.settings

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** How often the periodic sync runs (SPEC §5); [OFF] leaves only the open/save/manual syncs. */
enum class SyncInterval(val period: Duration?) {
    OFF(null),
    QUARTER_HOUR(15.minutes),
    HOUR(1.hours),
    SIX_HOURS(6.hours)
}
