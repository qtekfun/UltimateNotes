// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.work

import com.qtekfun.ultimatenotes.data.api.ApiError
import java.time.Instant

/** Why the last sync run failed, in terms a UI can explain. */
enum class SyncErrorKind {
    OFFLINE,
    UNAUTHORIZED,
    NOTES_APP_MISSING,
    UNSUPPORTED_API,

    /** Any other server-side or protocol failure. */
    SERVER;

    companion object {
        fun of(error: ApiError): SyncErrorKind = when (error) {
            ApiError.Offline -> OFFLINE

            ApiError.Unauthorized -> UNAUTHORIZED

            ApiError.NotesAppMissing -> NOTES_APP_MISSING

            ApiError.UnsupportedApi -> UNSUPPORTED_API

            is ApiError.Server, is ApiError.Conflict, ApiError.InvalidResponse,
            ApiError.NotFound, ApiError.Forbidden -> SERVER
        }
    }
}

/** What the sync is doing now; the UI observes it through [SyncStatusStore]. */
sealed interface SyncPhase {
    data object Idle : SyncPhase

    data object Syncing : SyncPhase

    /** The last run failed with [kind]; it stays until a run succeeds. */
    data class Error(val kind: SyncErrorKind) : SyncPhase
}

/** [lastSyncedAt] is the end of the last run that got through to the server, if any. */
data class SyncStatus(val phase: SyncPhase = SyncPhase.Idle, val lastSyncedAt: Instant? = null)
