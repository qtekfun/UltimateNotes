// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.store

import android.content.SharedPreferences
import androidx.core.content.edit
import com.qtekfun.ultimatenotes.sync.queue.SyncCheckpoint
import com.qtekfun.ultimatenotes.sync.queue.SyncCheckpointStore

/**
 * Keeps the [SyncCheckpoint] in a private [SharedPreferences] file. It holds no secrets and no
 * note content: only an ETag and a timestamp, which the server can always give again.
 */
class SharedPreferencesCheckpointStore(private val prefs: SharedPreferences) : SyncCheckpointStore {
    override suspend fun load(): SyncCheckpoint {
        val hasPrune = prefs.contains(KEY_PRUNE_BEFORE)
        return SyncCheckpoint(
            listEtag = prefs.getString(KEY_ETAG, null),
            pruneBefore = if (hasPrune) prefs.getLong(KEY_PRUNE_BEFORE, 0) else null
        )
    }

    override suspend fun save(checkpoint: SyncCheckpoint) = prefs.edit {
        remove(KEY_ETAG)
        remove(KEY_PRUNE_BEFORE)
        checkpoint.listEtag?.let { putString(KEY_ETAG, it) }
        checkpoint.pruneBefore?.let { putLong(KEY_PRUNE_BEFORE, it) }
    }

    override suspend fun clear() = prefs.edit {
        remove(KEY_ETAG)
        remove(KEY_PRUNE_BEFORE)
    }

    private companion object {
        const val KEY_ETAG = "list_etag"
        const val KEY_PRUNE_BEFORE = "prune_before"
    }
}
