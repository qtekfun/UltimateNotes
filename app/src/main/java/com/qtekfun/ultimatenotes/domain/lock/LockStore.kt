// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.lock

/**
 * The only lock state that survives process death: when the app last went to the background
 * (epoch millis). Null means "locked, or in the foreground", so a restored process whose app was
 * in the foreground when it died starts locked.
 */
interface LockStore {
    var backgroundedAt: Long?
}
