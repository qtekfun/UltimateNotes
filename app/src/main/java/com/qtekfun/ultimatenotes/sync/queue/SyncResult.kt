// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.queue

import com.qtekfun.ultimatenotes.data.api.ApiError

/** What one sync run did. */
data class SyncReport(
    /** Server notes inserted or updated locally. */
    val pulled: Int = 0,
    /** Notes removed locally because they no longer exist on the server. */
    val removed: Int = 0,
    /** Local creations and edits uploaded. */
    val pushed: Int = 0,
    /** Local deletions that reached the server. */
    val deleted: Int = 0,
    /** Conflicts resolved by keeping the local text as a new note. */
    val forked: Int = 0,
    /** Notes left for the next run (edited meanwhile, rejected by the server...): retry later. */
    val skipped: Int = 0
)

/** Outcome of a sync run: the engine never throws to its callers. */
sealed interface SyncResult {
    val report: SyncReport

    data class Success(override val report: SyncReport) : SyncResult

    /** The run stopped at [error]; [report] is what was done before. Ask [BackoffPolicy]. */
    data class Failed(val error: ApiError, override val report: SyncReport) : SyncResult
}
