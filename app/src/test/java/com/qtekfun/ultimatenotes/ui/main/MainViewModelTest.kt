// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.main

import app.cash.turbine.test
import com.qtekfun.ultimatenotes.data.settings.FakePreferences
import com.qtekfun.ultimatenotes.data.settings.SettingsRepository
import com.qtekfun.ultimatenotes.domain.folder.FolderNode
import com.qtekfun.ultimatenotes.domain.folder.FolderOverview
import com.qtekfun.ultimatenotes.domain.folder.FolderSelection
import com.qtekfun.ultimatenotes.domain.folder.ObserveFolders
import com.qtekfun.ultimatenotes.domain.list.NoteActions
import com.qtekfun.ultimatenotes.domain.list.NoteListItem
import com.qtekfun.ultimatenotes.domain.list.NoteSection
import com.qtekfun.ultimatenotes.domain.list.NoteSortOrder
import com.qtekfun.ultimatenotes.domain.list.ObserveNotes
import com.qtekfun.ultimatenotes.domain.sync.SyncTrigger
import com.qtekfun.ultimatenotes.sync.work.FakeStatusStore
import com.qtekfun.ultimatenotes.sync.work.SyncErrorKind
import com.qtekfun.ultimatenotes.sync.work.SyncPhase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
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
class MainViewModelTest {
    private val now = Instant.parse("2026-10-15T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("UTC"))
    private val overview =
        MutableStateFlow(FolderOverview(total = 3, folders = listOf(FolderNode("Work", 0, 3))))
    private val observeFolders = mockk<ObserveFolders> { every { this@mockk() } returns overview }

    private fun note(
        id: Long,
        daysAgo: Long = 0,
        category: String = "",
        favorite: Boolean = false
    ) = NoteListItem(
        id,
        "note $id",
        "",
        category,
        favorite,
        now.minusSeconds(daysAgo * 86_400)
    )

    private val notes = MutableStateFlow(
        listOf(
            note(1),
            note(2, daysAgo = 1, category = "Work"),
            note(3, daysAgo = 3, category = "Work/Meetings", favorite = true)
        )
    )
    private val observeNotes = mockk<ObserveNotes> { every { this@mockk() } returns notes }
    private val actions = mockk<NoteActions>(relaxed = true)
    private val preferences = FakePreferences()
    private val settings = SettingsRepository(preferences)
    private val syncStatus = FakeStatusStore()
    private var syncs = 0
    private var syncGate: CompletableDeferred<Unit>? = null
    private val trigger = SyncTrigger {
        syncs++
        syncGate?.await()
    }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.model() = MainViewModel(
        observeFolders,
        observeNotes,
        actions,
        settings,
        trigger,
        syncStatus,
        clock,
        backgroundScope
    )

    private fun MainUiState.ids() = groups.flatMap { g -> g.notes.map { it.localId } }

    @Test
    fun `starts on all notes with the folder overview and the grouped list`() = runTest {
        model().state.test {
            val state = expectMostRecentItem()
            assertEquals(FolderSelection.All, state.selection)
            assertEquals(overview.value, state.folders)
            assertTrue(state.loaded)
            assertEquals(
                listOf(NoteSection.Pinned, NoteSection.Today, NoteSection.Yesterday),
                state.groups.map { it.section }
            )
            assertEquals(listOf(3L, 1, 2), state.ids())
        }
    }

    @Test
    fun `before the first list arrives nothing is loaded`() = runTest {
        assertFalse(MainUiState().loaded)
        assertFalse(MainUiState().selecting)
    }

    @Test
    fun `selecting a folder changes what is shown`() = runTest {
        val model = model()
        model.state.test {
            expectMostRecentItem()
            model.select(FolderSelection.Folder("Work"))
            val state = awaitItem()
            assertEquals(FolderSelection.Folder("Work"), state.selection)
            assertEquals(listOf(3L, 2), state.ids())
        }
    }

    @Test
    fun `folder changes reach the drawer`() = runTest {
        model().state.test {
            expectMostRecentItem()
            overview.value = FolderOverview(total = 4, favorites = 1)
            assertEquals(4, awaitItem().folders.total)
        }
    }

    @Test
    fun `note changes reach the list`() = runTest {
        model().state.test {
            expectMostRecentItem()
            notes.value = notes.value + note(4)
            assertEquals(listOf(3L, 4, 1, 2), awaitItem().ids())
        }
    }

    @Test
    fun `the sort order comes from the settings`() = runTest {
        model().state.test {
            assertEquals(NoteSortOrder.MODIFIED, expectMostRecentItem().sortOrder)
            settings.setSortOrder(NoteSortOrder.TITLE)
            val state = awaitItem()
            assertEquals(NoteSortOrder.TITLE, state.sortOrder)
            assertEquals(
                listOf(NoteSection.Pinned, NoteSection.Alphabetical),
                state.groups.map {
                    it.section
                }
            )
        }
    }

    @Test
    fun `the model changes the sort order through the settings`() = runTest {
        val model = model()
        model.state.test {
            expectMostRecentItem()
            model.setSortOrder(NoteSortOrder.TITLE)
            assertEquals(NoteSortOrder.TITLE, awaitItem().sortOrder)
        }
    }

    @Test
    fun `toggling a favorite flips it`() = runTest {
        val model = model()
        model.toggleFavorite(note(1))
        model.toggleFavorite(note(3, favorite = true))
        coVerify { actions.setFavorite(listOf(1L), true) }
        coVerify { actions.setFavorite(listOf(3L), false) }
    }

    // --- multi-select -------------------------------------------------------------------------

    @Test
    fun `long press picks notes and picking the last one off ends the mode`() = runTest {
        val model = model()
        model.state.test {
            expectMostRecentItem()
            model.toggleSelected(1)
            assertEquals(setOf(1L), awaitItem().selectedIds)
            model.toggleSelected(2)
            assertTrue(awaitItem().selecting)
            model.toggleSelected(1)
            model.toggleSelected(2)
            assertFalse(expectMostRecentItem().selecting)
        }
    }

    @Test
    fun `clear and select all`() = runTest {
        val model = model()
        model.state.test {
            expectMostRecentItem()
            model.selectAll()
            assertEquals(setOf(1L, 2, 3), awaitItem().selectedIds)
            model.clearSelection()
            assertEquals(emptySet<Long>(), awaitItem().selectedIds)
        }
    }

    @Test
    fun `changing folder leaves multi-select`() = runTest {
        val model = model()
        model.state.test {
            expectMostRecentItem()
            model.toggleSelected(1)
            awaitItem()
            model.select(FolderSelection.Favorites)
            assertEquals(emptySet<Long>(), expectMostRecentItem().selectedIds)
        }
    }

    @Test
    fun `picked notes that leave the list are dropped from the selection`() = runTest {
        val model = model()
        model.state.test {
            expectMostRecentItem()
            model.toggleSelected(1)
            awaitItem()
            notes.value = notes.value.filter { it.localId != 1L }
            assertEquals(emptySet<Long>(), expectMostRecentItem().selectedIds)
        }
    }

    @Test
    fun `bulk favorite favorites the picked notes unless they all are`() = runTest {
        val model = model()
        model.state.test {
            expectMostRecentItem()
            model.toggleSelected(1)
            model.toggleSelected(3)
            expectMostRecentItem()
            model.favoriteSelected()
            coVerify { actions.setFavorite(listOf(1L, 3L), true) }
            assertFalse(expectMostRecentItem().selecting)

            model.toggleSelected(3)
            expectMostRecentItem()
            model.favoriteSelected()
            coVerify { actions.setFavorite(listOf(3L), false) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `bulk move sends the picked notes to the folder and leaves the mode`() = runTest {
        val model = model()
        model.state.test {
            expectMostRecentItem()
            model.toggleSelected(1)
            model.toggleSelected(2)
            expectMostRecentItem()
            model.moveSelected("Home")
            coVerify { actions.move(setOf(1L, 2L), "Home") }
            assertFalse(expectMostRecentItem().selecting)
        }
    }

    // --- delayed delete with undo -------------------------------------------------------------

    @Test
    fun `a swiped note is hidden at once and only deleted after the undo window`() = runTest {
        val model = model()
        model.state.test {
            expectMostRecentItem()
            model.delete(1)
            val state = expectMostRecentItem()
            assertEquals(listOf(3L, 2), state.ids())
            assertEquals(1, state.pendingDeletion?.count)
            coVerify(exactly = 0) { actions.delete(any()) }

            advanceTimeBy(4.seconds)
            runCurrent()
            coVerify(exactly = 0) { actions.delete(any()) }

            advanceTimeBy(1.seconds)
            runCurrent()
            coVerify(exactly = 1) { actions.delete(listOf(1L)) }
            assertNull(expectMostRecentItem().pendingDeletion)
        }
    }

    @Test
    fun `undo brings the note back and it is never deleted`() = runTest {
        val model = model()
        model.state.test {
            expectMostRecentItem()
            model.delete(1)
            expectMostRecentItem()
            advanceTimeBy(2.seconds)
            model.undoDelete()
            val state = expectMostRecentItem()
            assertEquals(listOf(3L, 1, 2), state.ids())
            assertNull(state.pendingDeletion)

            advanceTimeBy(10.seconds)
            runCurrent()
            coVerify(exactly = 0) { actions.delete(any()) }
        }
    }

    @Test
    fun `each deletion gets a new token so the snackbar shows again`() = runTest {
        val model = model()
        model.state.test {
            expectMostRecentItem()
            model.delete(1)
            val first = expectMostRecentItem().pendingDeletion!!
            model.delete(2)
            val second = expectMostRecentItem().pendingDeletion!!
            assertTrue(second.token > first.token)
            coVerify { actions.delete(listOf(1L)) }
        }
    }

    @Test
    fun `bulk delete hides every picked note and leaves the mode`() = runTest {
        val model = model()
        model.state.test {
            expectMostRecentItem()
            model.toggleSelected(1)
            model.toggleSelected(2)
            expectMostRecentItem()
            model.deleteSelected()
            val state = expectMostRecentItem()
            assertEquals(listOf(3L), state.ids())
            assertEquals(2, state.pendingDeletion?.count)
            assertFalse(state.selecting)
            advanceTimeBy(5.seconds)
            runCurrent()
            coVerify { actions.delete(listOf(1L, 2L)) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting with nothing picked does nothing`() = runTest {
        val model = model()
        model.state.test {
            expectMostRecentItem()
            model.deleteSelected()
            expectNoEvents()
        }
        advanceTimeBy(10.seconds)
        coVerify(exactly = 0) { actions.delete(any()) }
    }

    @Test
    fun `leaving the screen commits a pending deletion without waiting`() = runTest {
        val model = model()
        model.state.test {
            expectMostRecentItem()
            model.delete(1)
            expectMostRecentItem()
            model.flushDeletions()
            runCurrent()
            coVerify(exactly = 1) { actions.delete(listOf(1L)) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- pull to refresh ----------------------------------------------------------------------

    @Test
    fun `pull to refresh asks for a sync and shows the indicator until it ends`() = runTest {
        val gate = CompletableDeferred<Unit>()
        syncGate = gate
        val model = model()
        model.state.test {
            assertFalse(expectMostRecentItem().refreshing)
            model.refresh()
            assertTrue(awaitItem().refreshing)
            model.refresh() // ignored while one is running
            gate.complete(Unit)
            assertFalse(awaitItem().refreshing)
        }
        assertEquals(1, syncs)
    }

    @Test
    fun `a sync that runs in the background also shows as syncing and refreshing`() = runTest {
        val model = model()
        model.state.test {
            val idle = expectMostRecentItem()
            assertFalse(idle.syncing)
            assertFalse(idle.refreshing)
            syncStatus.markSyncing()
            val running = awaitItem()
            assertTrue(running.syncing)
            assertTrue(running.refreshing)
            syncStatus.markSynced(now)
            val done = awaitItem()
            assertFalse(done.syncing)
            assertFalse(done.refreshing)
            assertEquals(now, done.sync.lastSyncedAt)
        }
    }

    @Test
    fun `a failed sync is exposed until a later one succeeds`() = runTest {
        val model = model()
        model.state.test {
            expectMostRecentItem()
            syncStatus.markError(SyncErrorKind.OFFLINE)
            val failed = awaitItem()
            assertEquals(SyncPhase.Error(SyncErrorKind.OFFLINE), failed.sync.phase)
            assertFalse(failed.syncing)
        }
    }
}
