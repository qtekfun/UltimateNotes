// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.sync

/**
 * Lets the UI ask for a synchronization without knowing how it runs. The list's pull-to-refresh
 * uses it; T08 replaces the no-op binding in `SyncTriggerModule` with the real one.
 */
fun interface SyncTrigger {
    /** Syncs now; returns when the attempt is over, however it ended. */
    suspend fun requestSync()
}

/** Default until T08: there is nothing to sync yet. */
object NoopSyncTrigger : SyncTrigger {
    override suspend fun requestSync() = Unit
}
