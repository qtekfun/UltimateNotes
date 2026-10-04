// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.queue

/**
 * What the last complete pull learned: the list's ETag (for `If-None-Match`) and the server's
 * `Last-Modified` in epoch seconds (for `pruneBefore`).
 */
data class SyncCheckpoint(val listEtag: String? = null, val pruneBefore: Long? = null) {
    companion object {
        val NONE = SyncCheckpoint()
    }
}

/** Persistence of the [SyncCheckpoint] (see SharedPreferencesCheckpointStore). */
interface SyncCheckpointStore {
    suspend fun load(): SyncCheckpoint

    suspend fun save(checkpoint: SyncCheckpoint)

    suspend fun clear()
}
