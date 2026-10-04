// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.local.model

/** Where a local note stands against the server (SPEC §4, §5). */
enum class SyncState {
    /** Identical to the server copy, as of the note's `lastSyncedEtag`. */
    SYNCED,

    /** Existing note edited locally; its change still has to be uploaded. */
    DIRTY,

    /** Created locally, without a server id yet. */
    NEW,

    /** Deleted locally; kept as a tombstone until the server delete succeeds. */
    DELETED,

    /** Both sides changed; resolution is up to the sync layer (T07). */
    CONFLICT
}
