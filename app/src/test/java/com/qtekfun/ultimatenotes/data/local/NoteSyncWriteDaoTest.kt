// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.local

import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.model.SyncState
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NoteSyncWriteDaoTest {
    private val database = inMemoryDatabase()
    private val dao = database.noteSyncWriteDao()

    @AfterEach
    fun close() = database.close()

    private suspend fun stored(content: String, state: SyncState = SyncState.DIRTY): NoteEntity {
        val note = NoteEntity(id = 3, content = content, syncState = state, lastSyncedEtag = "e1")
        return note.copy(localId = dao.insert(note))
    }

    @Test
    fun `replace and delete only act on a row that is still what the caller saw`() = runBlocking {
        val seen = stored("seen")
        dao.update(seen.copy(content = "edited since"))

        assertFalse(dao.replaceIfUnchanged(seen, seen.copy(content = "from sync")))
        assertFalse(dao.deleteIfUnchanged(seen))
        assertNull(
            dao.forkIfUnchanged(
                seen,
                seen.copy(content = "from sync"),
                NoteEntity(content = "copy")
            )
        )

        assertEquals("edited since", dao.get(seen.localId)?.content)
        assertEquals(1, database.noteSyncDao().getAll().size)
    }

    @Test
    fun `a fork stores the copy and the replacement together`() = runBlocking {
        val seen = stored("mine")

        val copy = dao.forkIfUnchanged(
            seen,
            seen.copy(content = "theirs"),
            NoteEntity(content = "mine copy")
        )

        assertEquals("theirs", dao.get(seen.localId)?.content)
        assertEquals("mine copy", dao.get(checkNotNull(copy).localId)?.content)
    }

    @Test
    fun `completing a push for a row that no longer exists does nothing`() = runBlocking {
        dao.completePush(
            NoteEntity(localId = 99, content = "gone"),
            id = 5,
            etag = "e",
            title = "t"
        )

        assertEquals(emptyList<NoteEntity>(), database.noteSyncDao().getAll())
    }

    @Test
    fun `completing a push keeps a delete issued meanwhile`() = runBlocking {
        val pushed = stored("text")
        dao.update(pushed.copy(syncState = SyncState.DELETED))

        dao.completePush(pushed, id = 3, etag = "e2", title = "t")

        val row = checkNotNull(dao.get(pushed.localId))
        assertEquals(SyncState.DELETED, row.syncState)
        assertEquals("e2", row.lastSyncedEtag)
    }

    @Test
    fun `completing a push adopts the title the server stored`() = runBlocking {
        val pushed = stored("text").let { it.copy(title = "a/b").also { n -> dao.update(n) } }

        dao.completePush(pushed, id = 3, etag = "e2", title = "ab (2)")

        val row = checkNotNull(dao.get(pushed.localId))
        assertEquals("ab (2)", row.title)
        assertEquals(SyncState.SYNCED, row.syncState)
    }

    @Test
    fun `completing a push never overwrites a title edited meanwhile`() = runBlocking {
        val pushed = stored("text").let { it.copy(title = "sent").also { n -> dao.update(n) } }
        dao.update(pushed.copy(title = "typed while syncing"))

        dao.completePush(pushed, id = 3, etag = "e2", title = "sent")

        val row = checkNotNull(dao.get(pushed.localId))
        assertEquals("typed while syncing", row.title)
        assertEquals(SyncState.DIRTY, row.syncState)
        assertEquals("e2", row.lastSyncedEtag)
    }

    @Test
    fun `completing a push keeps the local title if the server returned a blank one`() =
        runBlocking {
            val pushed = stored("text").let { it.copy(title = "mine").also { n -> dao.update(n) } }

            dao.completePush(pushed, id = 3, etag = "e2", title = "")

            assertEquals("mine", checkNotNull(dao.get(pushed.localId)).title)
        }
}
