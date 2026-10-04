// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.queue

import com.qtekfun.ultimatenotes.data.api.NotesClient
import com.qtekfun.ultimatenotes.data.local.dao.NoteSyncDao
import com.qtekfun.ultimatenotes.data.local.dao.NoteSyncWriteDao
import com.qtekfun.ultimatenotes.sync.conflict.ConflictResolver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The sync queue (SPEC §5): the queue itself is the set of notes in state NEW, DIRTY and DELETED in
 * Room, so it survives restarts. One run pulls ([NotePuller]) and then pushes ([NotePusher]);
 * running it again after a failure or a crash is safe (idempotent). Runs are serialized. It never
 * throws network errors to callers and never logs note content.
 *
 * Pull: lists notes with the last list ETag and `pruneBefore` (a 304 means nothing changed),
 * following chunks. Notes deleted on the server are inferred only from a complete walk. A note
 * edited locally that also changed on the server goes through the [ConflictResolver].
 *
 * Push: NEW -> POST, DIRTY -> PUT with `If-Match` (412 -> conflict resolution), DELETED -> DELETE
 * (404 counts as done). Edits beat deletions in both directions: a DIRTY note that no longer exists
 * on the server (404, or absent from a complete walk) is recreated as a new note, and a note deleted
 * here but edited on the server comes back. So no text is lost; the one remaining window is another
 * client editing between our pull and our DELETE, which the API cannot guard (DELETE has no
 * `If-Match`).
 *
 * Every local write is a compare-and-set against the row the engine read, so an edit made while a
 * sync is in flight is never overwritten: that note is left for the next run.
 */
class SyncEngine(
    client: NotesClient,
    reads: NoteSyncDao,
    writes: NoteSyncWriteDao,
    checkpoints: SyncCheckpointStore,
    resolver: ConflictResolver,
    private val io: CoroutineDispatcher,
    chunkSize: Int = DEFAULT_CHUNK_SIZE
) {
    private val running = Mutex()
    private val puller = NotePuller(client, reads, writes, checkpoints, resolver, chunkSize)
    private val pusher = NotePusher(client, reads, writes, resolver)

    suspend fun sync(): SyncResult = running.withLock {
        withContext(io) {
            val counters = SyncCounters()
            val failure = puller.pull(counters) ?: pusher.push(counters)
            if (failure == null) {
                SyncResult.Success(counters.report())
            } else {
                SyncResult.Failed(failure, counters.report())
            }
        }
    }

    companion object {
        const val DEFAULT_CHUNK_SIZE = 100
    }
}
