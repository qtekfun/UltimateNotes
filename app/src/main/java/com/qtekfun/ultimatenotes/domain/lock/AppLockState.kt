// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.lock

import kotlinx.coroutines.flow.StateFlow

/**
 * Read-only view of the app lock, for anything that shows note content outside the main UI (the
 * widget, T15): while [locked] is true it must show no content.
 */
interface AppLockState {
    val locked: StateFlow<Boolean>
}
