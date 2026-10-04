// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.work

import java.time.Instant
import kotlinx.coroutines.flow.StateFlow

/** The observable, persistent sync status. Holds no note content. */
interface SyncStatusStore {
    val status: StateFlow<SyncStatus>

    fun markSyncing()

    /** A run reached the server: back to idle, error cleared. */
    fun markSynced(at: Instant)

    fun markError(kind: SyncErrorKind)
}
