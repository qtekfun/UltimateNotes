// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.queue

import com.qtekfun.ultimatenotes.data.api.ApiError
import com.qtekfun.ultimatenotes.data.api.ApiResult
import com.qtekfun.ultimatenotes.data.api.NoteDto
import com.qtekfun.ultimatenotes.data.api.NotesClient
import com.qtekfun.ultimatenotes.data.api.NotesListing
import com.qtekfun.ultimatenotes.data.api.NotesPage
import com.qtekfun.ultimatenotes.data.local.dao.NoteSyncDao
import com.qtekfun.ultimatenotes.data.local.dao.NoteSyncWriteDao
import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.model.SyncState
import com.qtekfun.ultimatenotes.sync.conflict.ConflictResolver
import com.qtekfun.ultimatenotes.sync.conflict.Resolution
import com.qtekfun.ultimatenotes.sync.mapper.toSyncedEntity

/** The pull half of a sync run: server list -> Room. See [SyncEngine]. */
internal class NotePuller(
    private val client: NotesClient,
    private val reads: NoteSyncDao,
    private val writes: NoteSyncWriteDao,
    private val checkpoints: SyncCheckpointStore,
    private val resolver: ConflictResolver,
    private val chunkSize: Int
) {
    private sealed interface Walk {
        data object Done : Walk

        data object NeedsFullList : Walk

        data class Failed(val error: ApiError) : Walk
    }

    private sealed interface Step {
        data class Stop(val walk: Walk) : Step

        data class More(val page: NotesPage) : Step
    }

    /** Returns the error that stopped the pull, or null. */
    suspend fun pull(counters: SyncCounters): ApiError? {
        val first = walk(checkpoints.load(), counters)
        if (first !is Walk.NeedsFullList) return first.error()
        // A pruned note we do not have: the checkpoint is stale. Start over without it.
        checkpoints.clear()
        val second = walk(SyncCheckpoint.NONE, counters)
        return if (second is Walk.NeedsFullList) ApiError.InvalidResponse else second.error()
    }

    private fun Walk.error(): ApiError? = (this as? Walk.Failed)?.error

    /** Walks every chunk of the list; deletions and the checkpoint only follow a complete walk. */
    private suspend fun walk(checkpoint: SyncCheckpoint, counters: SyncCounters): Walk {
        val seen = HashSet<Long>()
        val skippedBefore = counters.skipped
        var cursor: String? = null
        var head = SyncCheckpoint.NONE
        var stop: Walk? = null
        while (stop == null) {
            when (val step = step(checkpoint, cursor, seen, counters)) {
                is Step.Stop -> stop = step.walk

                is Step.More -> {
                    if (cursor ==
                        null
                    ) {
                        head = SyncCheckpoint(step.page.etag, step.page.lastModified)
                    }
                    cursor = step.page.nextCursor
                    if (cursor == null) stop = complete(seen, head, skippedBefore, counters)
                }
            }
        }
        return stop
    }

    /** One list request and its notes. The list ETag is only sent on the first request. */
    private suspend fun step(
        checkpoint: SyncCheckpoint,
        cursor: String?,
        seen: MutableSet<Long>,
        counters: SyncCounters
    ): Step {
        val ifNoneMatch = if (cursor == null) checkpoint.listEtag else null
        return when (
            val result = client.listNotes(
                ifNoneMatch,
                checkpoint.pruneBefore,
                chunkSize,
                cursor
            )
        ) {
            is ApiResult.Failure -> Step.Stop(Walk.Failed(result.error))

            is ApiResult.Success -> when (val listing = result.value) {
                NotesListing.NotModified -> Step.Stop(Walk.Done)

                is NotesListing.Page ->
                    if (applyPage(listing.page, seen, counters)) {
                        Step.More(listing.page)
                    } else {
                        Step.Stop(Walk.NeedsFullList)
                    }
            }
        }
    }

    /** Returns false if a bare (pruned) id is for a note we do not have, i.e. the checkpoint lies. */
    private suspend fun applyPage(
        page: NotesPage,
        seen: MutableSet<Long>,
        counters: SyncCounters
    ): Boolean {
        for (dto in page.notes) {
            seen += dto.id
            if (dto.content != null) {
                applyServerNote(dto, counters)
            } else if (writes.getByRemoteId(dto.id) == null) {
                return false
            }
        }
        return true
    }

    private suspend fun complete(
        seen: Set<Long>,
        head: SyncCheckpoint,
        skippedBefore: Int,
        counters: SyncCounters
    ): Walk {
        removeMissing(seen, counters)
        // A skipped note must be seen in full again: never prune it away.
        if (counters.skipped == skippedBefore) checkpoints.save(head)
        return Walk.Done
    }

    private suspend fun applyServerNote(dto: NoteDto, counters: SyncCounters) {
        val local = writes.getByRemoteId(dto.id)
        val applied: Boolean? = when {
            local == null -> {
                writes.insert(dto.toSyncedEntity())
                true
            }

            // An edit made elsewhere beats our delete (DELETE cannot be conditional), as an edit
            // made here beats a deletion elsewhere: the tombstone is replaced.
            local.syncState == SyncState.SYNCED || local.syncState == SyncState.DELETED ->
                adoptServer(local, dto)

            local.lastSyncedEtag == dto.etag -> null

            else -> resolve(local, dto, counters)
        }
        when (applied) {
            true -> counters.pulled++
            false -> counters.skipped++
            null -> Unit
        }
    }

    private suspend fun adoptServer(local: NoteEntity, dto: NoteDto): Boolean? = if (local.etag ==
        dto.etag
    ) {
        null
    } else {
        writes.replaceIfUnchanged(local, dto.toSyncedEntity(local.localId))
    }

    /** Applies the resolver's decision to a note that is edited locally. */
    private suspend fun resolve(
        local: NoteEntity,
        server: NoteDto,
        counters: SyncCounters
    ): Boolean = when (val resolution = resolver.resolve(local, server)) {
        is Resolution.Replace -> writes.replaceIfUnchanged(local, resolution.note)

        is Resolution.Fork -> {
            val copy = writes.forkIfUnchanged(local, resolution.original, resolution.copy)
            if (copy != null) counters.forked++
            copy != null
        }
    }

    /** After a complete walk, a local note with a server id that was not listed is gone there. */
    private suspend fun removeMissing(seen: Set<Long>, counters: SyncCounters) {
        for (note in reads.getAll()) {
            val id = note.id
            if (id != null && id !in seen) {
                val done = when (note.syncState) {
                    SyncState.SYNCED, SyncState.DELETED -> writes.deleteIfUnchanged(note)
                    else -> writes.replaceIfUnchanged(note, note.asNew())
                }
                if (done) counters.removed++ else counters.skipped++
            }
        }
    }
}
