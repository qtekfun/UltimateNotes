// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.list

import app.cash.turbine.test
import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.inMemoryDatabase
import com.qtekfun.ultimatenotes.data.local.model.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NoteActionsTest {
    private val database = inMemoryDatabase()
    private val dao = database.noteDao()
    private val actions = NoteActions(dao)

    @AfterEach
    fun close() = database.close()

    private suspend fun save(
        state: SyncState,
        remoteId: Long? = null,
        category: String = "",
        favorite: Boolean = false
    ) = dao.insert(
        NoteEntity(
            id = remoteId,
            modified = 100,
            title = "t",
            category = category,
            content = "body",
            favorite = favorite,
            syncState = state
        )
    )

    @Test
    fun `favoriting a synced note marks it dirty and keeps its date`() = runTest {
        val id = save(SyncState.SYNCED, remoteId = 1)
        actions.setFavorite(listOf(id), true)
        val note = dao.get(id)!!
        assertEquals(true, note.favorite)
        assertEquals(SyncState.DIRTY, note.syncState)
        assertEquals(100, note.modified)
    }

    @Test
    fun `favoriting keeps the sync state of notes that still have to be uploaded`() = runTest {
        val new = save(SyncState.NEW)
        val conflict = save(SyncState.CONFLICT, remoteId = 2)
        actions.setFavorite(listOf(new, conflict), true)
        assertEquals(SyncState.NEW, dao.get(new)!!.syncState)
        assertEquals(SyncState.CONFLICT, dao.get(conflict)!!.syncState)
    }

    @Test
    fun `a change that changes nothing leaves a synced note synced`() = runTest {
        val id = save(SyncState.SYNCED, remoteId = 1, favorite = true)
        actions.setFavorite(listOf(id), true)
        assertEquals(SyncState.SYNCED, dao.get(id)!!.syncState)
    }

    @Test
    fun `moving normalizes the folder and marks the note dirty`() = runTest {
        val a = save(SyncState.SYNCED, remoteId = 1, category = "Old")
        val b = save(SyncState.SYNCED, remoteId = 2)
        actions.move(listOf(a, b), "  Work / /Meetings/ ")
        assertEquals("Work/Meetings", dao.get(a)!!.category)
        assertEquals("Work/Meetings", dao.get(b)!!.category)
        assertEquals(SyncState.DIRTY, dao.get(b)!!.syncState)
    }

    @Test
    fun `moving to a blank folder means no folder`() = runTest {
        val id = save(SyncState.SYNCED, remoteId = 1, category = "Work")
        actions.move(listOf(id), "  ")
        assertEquals("", dao.get(id)!!.category)
    }

    @Test
    fun `deleting an uploaded note leaves a tombstone for the sync layer`() = runTest {
        val id = save(SyncState.SYNCED, remoteId = 1)
        actions.delete(listOf(id))
        assertEquals(SyncState.DELETED, dao.get(id)!!.syncState)
        dao.observeAll().test { assertEquals(emptyList<NoteEntity>(), awaitItem()) }
    }

    @Test
    fun `deleting a note never uploaded removes it outright`() = runTest {
        val id = save(SyncState.NEW)
        actions.delete(listOf(id))
        assertNull(dao.get(id))
    }

    @Test
    fun `a dirty or conflicting note is tombstoned`() = runTest {
        val dirty = save(SyncState.DIRTY, remoteId = 1)
        val conflict = save(SyncState.CONFLICT, remoteId = 2)
        actions.delete(listOf(dirty, conflict))
        assertEquals(SyncState.DELETED, dao.get(dirty)!!.syncState)
        assertEquals(SyncState.DELETED, dao.get(conflict)!!.syncState)
    }

    @Test
    fun `unknown and already deleted notes are skipped`() = runTest {
        val gone = save(SyncState.DELETED, remoteId = 3)
        actions.delete(listOf(gone, 999))
        actions.setFavorite(listOf(gone, 999), true)
        actions.move(listOf(gone, 999), "X")
        val note = dao.get(gone)!!
        assertEquals(SyncState.DELETED, note.syncState)
        assertEquals(false, note.favorite)
        assertEquals("", note.category)
    }

    @Test
    fun `normalizeCategory trims segments and drops empty ones`() {
        assertEquals("a/b", NoteActions.normalizeCategory(" a //b/ "))
        assertEquals("", NoteActions.normalizeCategory("/"))
    }

    @Test
    fun `observing notes maps them to rows and hides tombstones`() = runTest {
        val kept = dao.insert(
            NoteEntity(
                content = "# Hello\nworld",
                modified = 5,
                syncState = SyncState.SYNCED,
                id = 1
            )
        )
        save(SyncState.DELETED, remoteId = 2)
        ObserveNotes(dao, Dispatchers.Unconfined)().test {
            val rows = awaitItem()
            assertEquals(listOf(kept), rows.map { it.localId })
            assertEquals("Hello", rows.single().title)
            assertEquals("world", rows.single().preview)
        }
    }
}
