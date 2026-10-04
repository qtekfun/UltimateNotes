// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.local

import app.cash.turbine.test
import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.model.FolderCount
import com.qtekfun.ultimatenotes.data.local.model.SyncState
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NoteDaoTest {
    private val database = inMemoryDatabase()
    private val notes = database.noteDao()
    private val sync = database.noteSyncDao()

    @AfterEach
    fun close() = database.close()

    private fun note(
        title: String,
        category: String = "",
        modified: Long = 0,
        favorite: Boolean = false,
        state: SyncState = SyncState.SYNCED,
        id: Long? = null
    ) = NoteEntity(
        id = id,
        modified = modified,
        title = title,
        category = category,
        content = "body of $title",
        favorite = favorite,
        syncState = state
    )

    @Test
    fun `a note round-trips with every field and gets a local id`() = runTest {
        val saved = NoteEntity(
            id = 7,
            etag = "abc",
            readonly = true,
            modified = 1_700_000_000,
            title = "Title",
            category = "Work/Meetings",
            content = "# Title\nbody",
            favorite = true,
            syncState = SyncState.CONFLICT,
            lastSyncedEtag = "old"
        )

        val localId = notes.insert(saved)

        assertEquals(saved.copy(localId = localId), notes.get(localId))
        assertEquals(saved.copy(localId = localId), notes.getByRemoteId(7))
        assertNull(notes.getByRemoteId(8))
    }

    @Test
    fun `new notes have no remote id and the remote id is unique`() = runTest {
        val a = notes.insert(note("a", state = SyncState.NEW))
        notes.insert(note("b", state = SyncState.NEW))
        notes.insert(note("c", id = 1))

        assertNull(notes.get(a)!!.id)
        assertThrows<Exception> { notes.insert(note("d", id = 1)) }
    }

    @Test
    fun `all notes list favorites first, then newest, and hide deleted ones`() = runTest {
        notes.insert(note("old", modified = 1))
        notes.insert(note("new", modified = 3))
        notes.insert(note("fav", modified = 2, favorite = true))
        notes.insert(note("gone", modified = 9, state = SyncState.DELETED))

        notes.observeAll().test {
            assertEquals(listOf("fav", "new", "old"), awaitItem().map { it.title })
        }
    }

    @Test
    fun `a folder includes its subfolders but not look-alike siblings`() = runTest {
        notes.insert(note("direct", category = "Work", modified = 1))
        notes.insert(note("sub", category = "Work/Meetings", modified = 2))
        notes.insert(note("deep", category = "Work/Meetings/2026", modified = 3))
        notes.insert(note("sibling", category = "Workshop", modified = 4))
        notes.insert(note("other", category = "Home", modified = 5))
        notes.insert(note("none", modified = 6))
        notes.insert(note("deleted", category = "Work", state = SyncState.DELETED))

        notes.observeByFolder("Work").test {
            assertEquals(listOf("deep", "sub", "direct"), awaitItem().map { it.title })
        }
        notes.observeByFolder("Work/Meetings").test {
            assertEquals(listOf("deep", "sub"), awaitItem().map { it.title })
        }
    }

    @Test
    fun `folder names with SQL wildcards match literally`() = runTest {
        notes.insert(note("percent", category = "100%"))
        notes.insert(note("other", category = "1000"))
        notes.insert(note("underscore", category = "a_b"))
        notes.insert(note("letter", category = "axb"))

        notes.observeByFolder("100%").test {
            assertEquals(listOf("percent"), awaitItem().map { it.title })
        }
        notes.observeByFolder("a_b").test {
            assertEquals(listOf("underscore"), awaitItem().map { it.title })
        }
    }

    @Test
    fun `the empty folder holds only the notes without folder`() = runTest {
        notes.insert(note("none"))
        notes.insert(note("work", category = "Work"))

        notes.observeByFolder("").test {
            assertEquals(listOf("none"), awaitItem().map { it.title })
        }
    }

    @Test
    fun `favorites are the favorite notes that are not deleted, newest first`() = runTest {
        notes.insert(note("a", favorite = true, modified = 1))
        notes.insert(note("b", favorite = true, modified = 2))
        notes.insert(note("c", modified = 3))
        notes.insert(note("d", favorite = true, state = SyncState.DELETED))

        notes.observeFavorites().test {
            assertEquals(listOf("b", "a"), awaitItem().map { it.title })
        }
    }

    @Test
    fun `folder counts group by exact category and skip deleted notes`() = runTest {
        notes.insert(note("1"))
        notes.insert(note("2", category = "Work"))
        notes.insert(note("3", category = "Work"))
        notes.insert(note("4", category = "Work/Meetings"))
        notes.insert(note("5", category = "Work", state = SyncState.DELETED))
        notes.insert(note("6", category = "Trash", state = SyncState.DELETED))

        notes.observeFolderCounts().test {
            assertEquals(
                listOf(FolderCount("", 1), FolderCount("Work", 2), FolderCount("Work/Meetings", 1)),
                awaitItem()
            )
        }
    }

    @Test
    fun `a single note is observed through its changes and ends when deleted locally`() = runTest {
        val id = notes.insert(note("first"))

        notes.observe(id).test {
            assertEquals("first", awaitItem()!!.title)
            notes.update(notes.get(id)!!.copy(title = "second", syncState = SyncState.DIRTY))
            assertEquals("second", awaitItem()!!.title)
            notes.update(notes.get(id)!!.copy(syncState = SyncState.DELETED))
            assertNull(awaitItem())
        }
        assertEquals(SyncState.DELETED, notes.get(id)!!.syncState)
    }

    @Test
    fun `delete removes the row for good`() = runTest {
        val id = notes.insert(note("x"))

        notes.delete(id)

        assertNull(notes.get(id))
        assertEquals(emptyList<NoteEntity>(), sync.getAll())
    }

    @Test
    fun `sync queries return dirty, new and deleted notes in creation order`() = runTest {
        notes.insert(note("synced"))
        notes.insert(note("dirty", state = SyncState.DIRTY, id = 1))
        notes.insert(note("new", state = SyncState.NEW))
        notes.insert(note("deleted", state = SyncState.DELETED, id = 2))
        notes.insert(note("conflict", state = SyncState.CONFLICT, id = 3))

        assertEquals(listOf("dirty", "new", "deleted"), sync.getPendingSync().map { it.title })
        assertEquals(listOf("dirty"), sync.getDirty().map { it.title })
        assertEquals(listOf("new"), sync.getNew().map { it.title })
        assertEquals(listOf("deleted"), sync.getDeleted().map { it.title })
        assertEquals(listOf("conflict"), sync.getConflicts().map { it.title })
        assertEquals(5, sync.getAll().size)
    }

    @Test
    fun `the pending sync count follows the queue`() = runTest {
        sync.observePendingSyncCount().test {
            assertEquals(0, awaitItem())
            val id = notes.insert(note("n", state = SyncState.NEW))
            assertEquals(1, awaitItem())
            notes.update(notes.get(id)!!.copy(syncState = SyncState.SYNCED))
            assertEquals(0, awaitItem())
        }
    }

    @Test
    fun `modify stores the changed note and reports it`() = runTest {
        val id = notes.insert(note("n"))
        assertEquals(true, notes.modify(id) { it.copy(title = "changed") })
        assertEquals("changed", notes.get(id)!!.title)
    }

    @Test
    fun `modify leaves the note alone when the change is null`() = runTest {
        val id = notes.insert(note("n"))
        assertEquals(false, notes.modify(id) { null })
        assertEquals("n", notes.get(id)!!.title)
    }

    @Test
    fun `modify of a missing note changes nothing`() = runTest {
        var called = false
        assertEquals(
            false,
            notes.modify(404) {
                called = true
                it
            }
        )
        assertEquals(false, called)
    }
}
