// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.queue

import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.model.SyncState

/** What one run did so far; becomes the [SyncReport]. Not thread-safe: a run is sequential. */
internal class SyncCounters {
    var pulled = 0
    var removed = 0
    var pushed = 0
    var deleted = 0
    var forked = 0
    var skipped = 0

    fun report() = SyncReport(pulled, removed, pushed, deleted, forked, skipped)

    /** Counts a note left for the next run; returns null so callers can `return counters.skip()`. */
    fun <T> skip(): T? {
        skipped++
        return null
    }
}

/** The note as a fresh local creation: the server no longer knows it, its text still matters. */
internal fun NoteEntity.asNew() =
    copy(id = null, etag = "", lastSyncedEtag = null, base = null, syncState = SyncState.NEW)
