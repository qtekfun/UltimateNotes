// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync

import com.qtekfun.ultimatenotes.data.api.NotesClient
import com.qtekfun.ultimatenotes.data.local.UltimateNotesDatabase
import com.qtekfun.ultimatenotes.data.local.dao.NoteSyncWriteDao
import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.inMemoryDatabase
import com.qtekfun.ultimatenotes.data.local.model.SyncState
import com.qtekfun.ultimatenotes.sync.conflict.ConflictResolver
import com.qtekfun.ultimatenotes.sync.queue.SyncCheckpoint
import com.qtekfun.ultimatenotes.sync.queue.SyncCheckpointStore
import com.qtekfun.ultimatenotes.sync.queue.SyncEngine
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers

class InMemoryCheckpointStore : SyncCheckpointStore {
    var current = SyncCheckpoint.NONE

    override suspend fun load() = current

    override suspend fun save(checkpoint: SyncCheckpoint) {
        current = checkpoint
    }

    override suspend fun clear() {
        current = SyncCheckpoint.NONE
    }
}

val TEST_CLOCK: Clock = Clock.fixed(Instant.parse("2026-10-04T10:00:00Z"), ZoneOffset.UTC)

/**
 * One simulated client: its own Room database and checkpoint, talking through [client] (a fake
 * server shared between fixtures, or a real Nextcloud in the E2E suite).
 */
class SyncFixture(
    client: NotesClient,
    wrapDao: (NoteSyncWriteDao) -> NoteSyncWriteDao = { it },
    chunkSize: Int = CHUNK
) {
    constructor(
        server: FakeNotesServer = FakeNotesServer(),
        wrapDao: (NoteSyncWriteDao) -> NoteSyncWriteDao = { it }
    ) : this(NotesClient(server, Dispatchers.Unconfined), wrapDao)

    val database: UltimateNotesDatabase = inMemoryDatabase()
    val dao: NoteSyncWriteDao = database.noteSyncWriteDao()
    private val reads = database.noteSyncDao()
    val checkpoints = InMemoryCheckpointStore()
    val engine = SyncEngine(
        client = client,
        reads = reads,
        writes = wrapDao(dao),
        checkpoints = checkpoints,
        resolver = ConflictResolver(TEST_CLOCK),
        io = Dispatchers.Unconfined,
        chunkSize = chunkSize
    )

    suspend fun sync() = engine.sync()

    suspend fun all(): List<NoteEntity> = reads.getAll()

    suspend fun byContent(text: String): NoteEntity = all().single { it.content == text }

    /** What the user sees: notes not deleted, as (content, category, favorite), sorted. */
    suspend fun visible(): List<Triple<String, String, Boolean>> = all()
        .filter { it.syncState != SyncState.DELETED }
        .map { Triple(it.content, it.category, it.favorite) }
        .sortedWith(compareBy({ it.first }, { it.second }, { it.third }))

    // What the UI will do (T10/T11), reproduced here to drive the tests.

    suspend fun create(
        content: String,
        category: String = "",
        title: String = content.lines().first()
    ): Long = dao.insert(NoteEntity(content = content, category = category, title = title))

    suspend fun retitle(localId: Long, title: String) {
        val note = checkNotNull(dao.get(localId))
        dao.update(note.copy(title = title, syncState = dirtied(note)))
    }

    suspend fun edit(localId: Long, content: String) {
        val note = checkNotNull(dao.get(localId))
        dao.update(note.copy(content = content, syncState = dirtied(note)))
    }

    suspend fun move(localId: Long, category: String) {
        val note = checkNotNull(dao.get(localId))
        dao.update(note.copy(category = category, syncState = dirtied(note)))
    }

    suspend fun favorite(localId: Long, favorite: Boolean) {
        val note = checkNotNull(dao.get(localId))
        dao.update(note.copy(favorite = favorite, syncState = dirtied(note)))
    }

    suspend fun delete(localId: Long) {
        val note = checkNotNull(dao.get(localId))
        if (note.id ==
            null
        ) {
            dao.delete(localId)
        } else {
            dao.update(note.copy(syncState = SyncState.DELETED))
        }
    }

    private fun dirtied(note: NoteEntity) = if (note.syncState ==
        SyncState.NEW
    ) {
        SyncState.NEW
    } else {
        SyncState.DIRTY
    }

    fun close() = database.close()

    companion object {
        const val CHUNK = 2
    }
}
