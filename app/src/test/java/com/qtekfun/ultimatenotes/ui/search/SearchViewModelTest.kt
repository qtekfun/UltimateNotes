// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.search

import app.cash.turbine.test
import com.qtekfun.ultimatenotes.domain.folder.FolderSelection
import com.qtekfun.ultimatenotes.domain.search.Highlighted
import com.qtekfun.ultimatenotes.domain.search.NoteSearcher
import com.qtekfun.ultimatenotes.domain.search.SearchQuery
import com.qtekfun.ultimatenotes.domain.search.SearchResult
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    /** Answers from [notes] and remembers what it was asked, so tests can count searches. */
    private class FakeSearcher : NoteSearcher {
        val calls = mutableListOf<Pair<SearchQuery, String?>>()
        val notes = MutableStateFlow<List<SearchResult>>(emptyList())

        override fun search(query: SearchQuery, folder: String?): Flow<List<SearchResult>> {
            calls += query to folder
            return notes
        }
    }

    private val searcher = FakeSearcher()

    private fun result(id: Long) = SearchResult(
        localId = id,
        title = Highlighted("note $id", emptyList()),
        snippet = Highlighted.EMPTY,
        category = "",
        favorite = false,
        modified = Instant.EPOCH
    )

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun model() = SearchViewModel(searcher, DEBOUNCE)

    @Test
    fun `starts closed and idle`() = runTest {
        model().state.test {
            assertEquals(SearchUiState(), expectMostRecentItem())
        }
        assertEquals(emptyList<Any>(), searcher.calls)
    }

    @Test
    fun `typing waits for a pause before searching`() = runTest {
        val model = model()
        model.state.test {
            expectMostRecentItem()
            model.setQuery("m")
            model.setQuery("mi")
            advanceTimeBy(DEBOUNCE - 1)
            runCurrent()
            assertEquals(emptyList<Any>(), searcher.calls)
            assertEquals(SearchStatus.SEARCHING, model.state.value.status)

            searcher.notes.value = listOf(result(1))
            advanceTimeBy(1)
            runCurrent()

            assertEquals(listOf(SearchQuery.parse("mi")!! to null), searcher.calls)
            assertEquals(SearchStatus.RESULTS, model.state.value.status)
            assertEquals(listOf(1L), model.state.value.results.map { it.localId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an edit that does not change the words does not search again`() = runTest {
        val model = model()
        model.state.test {
            expectMostRecentItem()
            model.setQuery("milk")
            advanceTimeBy(DEBOUNCE)
            runCurrent()
            model.setQuery("MILK  ")
            advanceTimeBy(DEBOUNCE)
            runCurrent()

            assertEquals(1, searcher.calls.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no results is reported and new results replace it`() = runTest {
        val model = model()
        model.state.test {
            expectMostRecentItem()
            model.setQuery("zzz")
            advanceTimeBy(DEBOUNCE)
            runCurrent()
            assertEquals(SearchStatus.NO_RESULTS, model.state.value.status)

            searcher.notes.value = listOf(result(7), result(8))
            runCurrent()

            assertEquals(SearchStatus.RESULTS, model.state.value.status)
            assertEquals(listOf(7L, 8L), model.state.value.results.map { it.localId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearing the box answers at once without a search`() = runTest {
        val model = model()
        model.state.test {
            expectMostRecentItem()
            searcher.notes.value = listOf(result(1))
            model.setQuery("a")
            advanceTimeBy(DEBOUNCE)
            runCurrent()
            assertEquals(SearchStatus.RESULTS, model.state.value.status)

            model.setQuery("")
            runCurrent()

            val state = model.state.value
            assertEquals(SearchStatus.IDLE, state.status)
            assertEquals(emptyList<SearchResult>(), state.results)
            assertEquals(1, searcher.calls.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `text with nothing searchable stays idle`() = runTest {
        val model = model()
        model.state.test {
            expectMostRecentItem()
            model.setQuery("\"*-")
            advanceTimeBy(DEBOUNCE)
            runCurrent()

            assertEquals(SearchStatus.IDLE, model.state.value.status)
            assertEquals("\"*-", model.state.value.query)
            assertEquals(emptyList<Any>(), searcher.calls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the folder chip appears only for a folder or no folder`() = runTest {
        val model = model()
        model.state.test {
            assertNull(expectMostRecentItem().scopeFolder)

            model.setListFolder(FolderSelection.Folder("Work"))
            assertEquals(FolderSelection.Folder("Work"), awaitItem().scopeFolder)

            model.setListFolder(FolderSelection.Favorites)
            assertNull(awaitItem().scopeFolder)

            model.setListFolder(FolderSelection.NoFolder)
            assertEquals(FolderSelection.NoFolder, awaitItem().scopeFolder)

            model.setListFolder(FolderSelection.All)
            assertNull(awaitItem().scopeFolder)
        }
    }

    @Test
    fun `scoping to the current folder searches it again, and all searches everywhere`() = runTest {
        val model = model()
        model.state.test {
            expectMostRecentItem()
            model.setListFolder(FolderSelection.Folder("Work/Meetings"))
            model.setQuery("agenda")
            advanceTimeBy(DEBOUNCE)
            runCurrent()
            model.setScope(SearchScope.FOLDER)
            runCurrent()
            model.setScope(SearchScope.ALL)
            runCurrent()

            val query = SearchQuery.parse("agenda")!!
            assertEquals(
                listOf(query to null, query to "Work/Meetings", query to null),
                searcher.calls
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the no folder scope searches notes without a folder`() = runTest {
        val model = model()
        model.state.test {
            expectMostRecentItem()
            model.setListFolder(FolderSelection.NoFolder)
            model.setScope(SearchScope.FOLDER)
            model.setQuery("x")
            advanceTimeBy(DEBOUNCE)
            runCurrent()

            assertEquals(listOf(SearchQuery.parse("x")!! to ""), searcher.calls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the folder scope falls back to everywhere when the list is not in a folder`() = runTest {
        val model = model()
        model.state.test {
            expectMostRecentItem()
            model.setListFolder(FolderSelection.Favorites)
            model.setScope(SearchScope.FOLDER)
            model.setQuery("x")
            advanceTimeBy(DEBOUNCE)
            runCurrent()

            assertEquals(listOf(SearchQuery.parse("x")!! to null), searcher.calls)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `closing the search forgets the query and scope`() = runTest {
        val model = model()
        model.state.test {
            expectMostRecentItem()
            model.open()
            assertTrue(awaitItem().active)

            model.setQuery("abc")
            model.setScope(SearchScope.FOLDER)
            runCurrent()
            model.close()
            runCurrent()

            val state = model.state.value
            assertFalse(state.active)
            assertEquals("", state.query)
            assertEquals(SearchScope.ALL, state.scope)
            assertEquals(SearchStatus.IDLE, state.status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        const val DEBOUNCE = 200L
    }
}
