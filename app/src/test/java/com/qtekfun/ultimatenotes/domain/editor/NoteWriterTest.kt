// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.editor

import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.inMemoryDatabase
import com.qtekfun.ultimatenotes.data.local.model.SyncState
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NoteWriterTest {
    private val database = inMemoryDatabase()
    private val dao = database.noteDao()
    private val now = Instant.parse("2026-10-15T12:00:00Z")
    private val writer = NoteWriter(dao, Clock.fixed(now, ZoneOffset.UTC))

    private var nextRemoteId = 1L

    @AfterEach
    fun close() = database.close()

    private suspend fun stored(state: SyncState, readonly: Boolean = false) = dao.insert(
        NoteEntity(
            id = if (state == SyncState.NEW) null else nextRemoteId++,
            etag = "e1",
            readonly = readonly,
            modified = 100,
            title = "old",
            category = "Work",
            content = "old",
            favorite = true,
            syncState = state,
            lastSyncedEtag = "e1"
        )
    )

    @Test
    fun `a new note is stored as NEW with a derived title in the given folder`() = runTest {
        val id = writer.create("# Plan\nbody", " Work / Plans ", favorite = true)!!
        val note = dao.get(id)!!
        assertEquals("Plan", note.title)
        assertEquals("# Plan\nbody", note.content)
        assertEquals("Work/Plans", note.category)
        assertTrue(note.favorite)
        assertEquals(SyncState.NEW, note.syncState)
        assertNull(note.id)
        assertEquals(now.epochSecond, note.modified)
    }

    @Test
    fun `a blank new note is not stored`() = runTest {
        assertNull(writer.create(" \n\t", "", favorite = false))
        assertNull(writer.create("", "", favorite = false))
        assertEquals(emptyList<NoteEntity>(), dao.observeAll().first())
    }

    @Test
    fun `editing a synced note makes it dirty and bumps the date`() = runTest {
        val id = stored(SyncState.SYNCED)
        assertTrue(writer.update(id, "# New title\nx"))
        val note = dao.get(id)!!
        assertEquals("# New title\nx", note.content)
        assertEquals("New title", note.title)
        assertEquals(SyncState.DIRTY, note.syncState)
        assertEquals(now.epochSecond, note.modified)
        assertEquals("e1", note.lastSyncedEtag)
        assertEquals("Work", note.category)
        assertTrue(note.favorite)
    }

    @Test
    fun `editing keeps NEW, DIRTY and CONFLICT as they are`() = runTest {
        for (state in listOf(SyncState.NEW, SyncState.DIRTY, SyncState.CONFLICT)) {
            val id = stored(state)
            assertTrue(writer.update(id, "edited"))
            assertEquals(state, dao.get(id)!!.syncState)
        }
    }

    @Test
    fun `unchanged text writes nothing`() = runTest {
        val id = stored(SyncState.SYNCED)
        assertFalse(writer.update(id, "old"))
        val note = dao.get(id)!!
        assertEquals(SyncState.SYNCED, note.syncState)
        assertEquals(100, note.modified)
    }

    @Test
    fun `read-only, deleted and missing notes are never written`() = runTest {
        val readonly = stored(SyncState.SYNCED, readonly = true)
        assertFalse(writer.update(readonly, "edited"))
        assertEquals("old", dao.get(readonly)!!.content)

        val deleted = stored(SyncState.DELETED)
        assertFalse(writer.update(deleted, "edited"))
        assertEquals("old", dao.get(deleted)!!.content)

        assertFalse(writer.update(404, "edited"))
    }
}
