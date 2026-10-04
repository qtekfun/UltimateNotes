// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.queue

import com.qtekfun.ultimatenotes.data.api.ApiError
import com.qtekfun.ultimatenotes.data.api.ApiResult
import com.qtekfun.ultimatenotes.data.api.NoteDto
import com.qtekfun.ultimatenotes.data.api.NotesClient
import com.qtekfun.ultimatenotes.data.local.dao.NoteSyncDao
import com.qtekfun.ultimatenotes.data.local.dao.NoteSyncWriteDao
import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.model.SyncState
import com.qtekfun.ultimatenotes.sync.conflict.ConflictResolver
import com.qtekfun.ultimatenotes.sync.conflict.Resolution
import com.qtekfun.ultimatenotes.sync.mapper.localEtag
import com.qtekfun.ultimatenotes.sync.mapper.toWriteDto

/** The push half of a sync run: pending local notes -> server. See [SyncEngine]. */
internal class NotePusher(
    private val client: NotesClient,
    private val reads: NoteSyncDao,
    private val writes: NoteSyncWriteDao,
    private val resolver: ConflictResolver
) {
    /** Returns the systemic error that stopped the queue, or null if it was worked through. */
    suspend fun push(counters: SyncCounters): ApiError? {
        for (note in reads.getPendingSync()) {
            val failure = pushOne(note, counters)
            if (failure != null) return failure
        }
        return null
    }

    private suspend fun pushOne(note: NoteEntity, counters: SyncCounters): ApiError? =
        when (note.syncState) {
            SyncState.NEW -> create(note, counters)
            SyncState.DELETED -> delete(note, counters)
            else -> update(note, counters)
        }

    private suspend fun create(note: NoteEntity, counters: SyncCounters): ApiError? =
        when (val result = client.createNote(note.toWriteDto())) {
            is ApiResult.Success -> {
                writes.completePush(
                    note,
                    result.value.id,
                    result.value.localEtag,
                    result.value.title
                )
                counters.pushed++
                null
            }

            is ApiResult.Failure -> failure(result.error, counters)
        }

    private suspend fun update(note: NoteEntity, counters: SyncCounters): ApiError? {
        val id = note.id
        val base = note.lastSyncedEtag
        return when {
            id == null -> create(note, counters)

            // No known server version to guard the write with: never overwrite blindly.
            base == null -> counters.skip()

            else -> send(note, id, base, counters)
        }
    }

    private suspend fun send(
        note: NoteEntity,
        id: Long,
        base: String,
        counters: SyncCounters
    ): ApiError? = when (val result = client.updateNote(id, base, note.toWriteDto())) {
        is ApiResult.Success -> {
            writes.completePush(note, id, result.value.localEtag, result.value.title)
            counters.pushed++
            null
        }

        is ApiResult.Failure -> when (val error = result.error) {
            is ApiError.Conflict -> resolveRejected(note, error.serverNote, counters)
            ApiError.NotFound -> recreate(note, counters)
            else -> failure(error, counters)
        }
    }

    /** The server refused an edit (412): resolve against the version it sent back. */
    private suspend fun resolveRejected(
        note: NoteEntity,
        server: NoteDto?,
        counters: SyncCounters
    ): ApiError? {
        if (server == null) return counters.skip()
        return when (val resolution = resolver.resolve(note, server)) {
            is Resolution.Replace -> {
                if (writes.replaceIfUnchanged(
                        note,
                        resolution.note
                    )
                ) {
                    counters.pulled++
                } else {
                    counters.skipped++
                }
                null
            }

            is Resolution.Fork -> {
                val copy = writes.forkIfUnchanged(note, resolution.original, resolution.copy)
                if (copy == null) {
                    counters.skip()
                } else {
                    counters.forked++
                    create(copy, counters)
                }
            }
        }
    }

    /** The server lost the note but we hold newer local text: upload it as a new note. */
    private suspend fun recreate(note: NoteEntity, counters: SyncCounters): ApiError? {
        val fresh = note.asNew()
        return if (writes.replaceIfUnchanged(
                note,
                fresh
            )
        ) {
            create(fresh, counters)
        } else {
            counters.skip()
        }
    }

    private suspend fun delete(note: NoteEntity, counters: SyncCounters): ApiError? {
        val id = note.id
        val error = if (id == null) null else (client.deleteNote(id) as? ApiResult.Failure)?.error
        return when (error) {
            // Never uploaded, deleted, or already gone from the server: the tombstone is done.
            null, ApiError.NotFound -> {
                writes.deleteIfUnchanged(note)
                if (id != null) counters.deleted++
                null
            }

            else -> failure(error, counters)
        }
    }

    /**
     * Systemic errors stop the run; anything specific to one note is counted as skipped so the
     * rest of the queue still goes out.
     */
    private fun failure(error: ApiError, counters: SyncCounters): ApiError? = when (error) {
        ApiError.Offline, ApiError.Unauthorized, ApiError.NotesAppMissing,
        ApiError.UnsupportedApi -> error

        else -> counters.skip()
    }
}
