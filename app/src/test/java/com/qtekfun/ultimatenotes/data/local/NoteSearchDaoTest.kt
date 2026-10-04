// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.local

import app.cash.turbine.test
import com.qtekfun.ultimatenotes.data.local.dao.search
import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.model.SearchMarkers
import com.qtekfun.ultimatenotes.data.local.model.SyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NoteSearchDaoTest {
    private val database = inMemoryDatabase()
    private val notes = database.noteDao()
    private val search = database.noteSearchDao()

    @AfterEach
    fun close() = database.close()

    private suspend fun add(
        title: String,
        content: String,
        category: String = "",
        state: SyncState = SyncState.SYNCED,
        modified: Long = 0
    ) = notes.insert(
        NoteEntity(
            title = title,
            content = content,
            category = category,
            syncState = state,
            modified = modified
        )
    )

    @Test
    fun `finds notes by title or content, ignoring case`() = runTest {
        add("Groceries", "milk and eggs")
        add("Meeting", "talk about GROCERIES budget")
        add("Other", "nothing here")

        search.search("groceries").test {
            assertEquals(setOf("Groceries", "Meeting"), awaitItem().map { it.title }.toSet())
        }
    }

    @Test
    fun `the last word matches as a prefix and all words must match`() = runTest {
        add("Recipe", "chocolate cake with almonds")
        add("Other", "chocolate bar")

        search.search("chocolate ca").test {
            assertEquals(listOf("Recipe"), awaitItem().map { it.title })
        }
    }

    @Test
    fun `the snippet wraps the match in markers`() = runTest {
        add("Note", "a long text that mentions the needle somewhere in the middle")

        search.search("needle").test {
            val snippet = awaitItem().single().snippet
            assertTrue(
                snippet.contains("${SearchMarkers.OPEN}needle${SearchMarkers.CLOSE}"),
                snippet
            )
        }
    }

    @Test
    fun `the index follows edits and deletions of the notes`() = runTest {
        val id = add("Note", "before")
        assertEquals(1, search.search("before").first().size)

        notes.update(notes.get(id)!!.copy(content = "after"))
        assertEquals(0, search.search("before").first().size)
        assertEquals(1, search.search("after").first().size)

        notes.delete(id)
        assertEquals(0, search.search("after").first().size)
    }

    @Test
    fun `notes deleted locally do not match`() = runTest {
        add("Gone", "findme", state = SyncState.DELETED)
        add("Here", "findme")

        search.search("findme").test {
            assertEquals(listOf("Here"), awaitItem().map { it.title })
        }
    }

    @Test
    fun `a folder filter keeps its subfolders only`() = runTest {
        add("a", "word", category = "Work")
        add("b", "word", category = "Work/Meetings")
        add("c", "word", category = "Workshop")
        add("d", "word")

        search.search("word", folder = "Work").test {
            assertEquals(setOf("a", "b"), awaitItem().map { it.title }.toSet())
        }
        assertEquals(4, search.search("word").first().size)
    }

    @Test
    fun `results are newest first`() = runTest {
        add("old", "word", modified = 1)
        add("new", "word", modified = 2)
        val fav = add("fav", "word", modified = 0)
        notes.update(notes.get(fav)!!.copy(favorite = true))

        search.search("word").test {
            assertEquals(listOf("new", "old", "fav"), awaitItem().map { it.title })
        }
    }

    @Test
    fun `punctuation and FTS operators typed by the user are plain text`() = runTest {
        add("Quote", "she said \"hello\" OR goodbye")

        search.search("\"hello\" OR").test { assertEquals(1, awaitItem().size) }
        search.search("NEAR(").test { assertEquals(0, awaitItem().size) }
    }

    @Test
    fun `a blank query finds nothing`() = runTest {
        add("Note", "text")

        search.search("   ").test {
            assertEquals(emptyList<Any>(), awaitItem())
            awaitComplete()
        }
    }
}
