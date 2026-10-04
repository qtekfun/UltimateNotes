// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.search

import app.cash.turbine.test
import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.inMemoryDatabase
import com.qtekfun.ultimatenotes.domain.TextRange
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoomNoteSearcherTest {
    private val database = inMemoryDatabase()
    private val notes = database.noteDao()
    private val searcher = RoomNoteSearcher(database.noteSearchDao(), UnconfinedTestDispatcher())

    @AfterEach
    fun close() = database.close()

    private suspend fun add(
        title: String,
        content: String = "",
        category: String = "",
        modified: Long = 0
    ) = notes.insert(
        NoteEntity(title = title, content = content, category = category, modified = modified)
    )

    private suspend fun titles(input: String, folder: String? = null): List<String> {
        val query = SearchQuery.parse(input)!!
        return searcher.search(query, folder).let { flow ->
            var found = emptyList<String>()
            flow.test {
                found = awaitItem().map { it.title.text }
                cancelAndIgnoreRemainingEvents()
            }
            found
        }
    }

    @Test
    fun `accents and case do not matter, in the notes or in the query`() = runTest {
        add("Café con leche")
        add("AÑO nuevo")
        add("plain")

        assertEquals(listOf("Café con leche"), titles("CAFE"))
        assertEquals(listOf("Café con leche"), titles("café"))
        assertEquals(listOf("AÑO nuevo"), titles("ano"))
        assertEquals(listOf("AÑO nuevo"), titles("AÑO"))
    }

    @Test
    fun `a title match ranks above a mention, then the newest comes first`() = runTest {
        add("shopping", "buy milk", modified = 30)
        add("Milk run", "", modified = 10)
        add("notes", "milk again", modified = 20)
        add("Milk", "milk milk", modified = 5)

        assertEquals(listOf("Milk run", "Milk", "shopping", "notes"), titles("milk"))
    }

    @Test
    fun `more query words in the title rank higher`() = runTest {
        add("red", "red apple", modified = 100)
        add("red apple", "", modified = 1)

        assertEquals(listOf("red apple", "red"), titles("red apple"))
    }

    @Test
    fun `results carry highlighted title, snippet, folder and date`() = runTest {
        add("Café menu", "the best cafe in town", category = "Food", modified = 42)

        searcher.search(SearchQuery.parse("cafe")!!, null).test {
            val result = awaitItem().single()
            assertEquals(Highlighted("Café menu", listOf(TextRange(0, 4))), result.title)
            assertEquals("the best cafe in town", result.snippet.text)
            assertEquals(listOf(TextRange(9, 13)), result.snippet.ranges)
            assertEquals("Food", result.category)
            assertEquals(Instant.ofEpochSecond(42), result.modified)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a title only match still has a preview of the text`() = runTest {
        add("Groceries", "milk\r\n\r\neggs")

        searcher.search(SearchQuery.parse("groceries")!!, null).test {
            val result = awaitItem().single()
            assertEquals("milk eggs", result.snippet.text)
            assertEquals(emptyList<TextRange>(), result.snippet.ranges)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a folder limits the search to it and its subfolders`() = runTest {
        add("a word", category = "Work")
        add("b word", category = "Work/Meetings")
        add("c word", category = "Workshop")
        add("d word")

        assertEquals(setOf("a word", "b word"), titles("word", "Work").toSet())
        assertEquals(listOf("d word"), titles("word", ""))
        assertEquals(4, titles("word").size)
    }

    @Test
    fun `the last word matches as a prefix`() = runTest {
        add("chocolate cake")

        assertEquals(listOf("chocolate cake"), titles("chocolate ca"))
        assertEquals(emptyList<String>(), titles("choc cake"))
    }

    @Test
    fun `emoji in notes do not break search or highlights`() = runTest {
        add("😀 smile", "😀😀 smile 😀")

        searcher.search(SearchQuery.parse("smile")!!, null).test {
            val result = awaitItem().single()
            assertEquals(listOf(TextRange(3, 8)), result.title.ranges)
            assertEquals(
                listOf("smile"),
                result.snippet.ranges.map { result.snippet.text.substring(it.start, it.end) }
            )
            cancelAndIgnoreRemainingEvents()
        }
    }
}
