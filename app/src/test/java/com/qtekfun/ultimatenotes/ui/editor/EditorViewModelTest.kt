// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.inMemoryDatabase
import com.qtekfun.ultimatenotes.data.local.model.SyncState
import com.qtekfun.ultimatenotes.domain.editor.NoteWriter
import com.qtekfun.ultimatenotes.domain.folder.ObserveFolders
import com.qtekfun.ultimatenotes.domain.list.NoteActions
import com.qtekfun.ultimatenotes.domain.markdown.BlockKind
import com.qtekfun.ultimatenotes.domain.markdown.FormatAction
import com.qtekfun.ultimatenotes.domain.markdown.InlineStyle
import com.qtekfun.ultimatenotes.domain.sync.SyncTrigger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.spyk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalFoundationApi::class)
class EditorViewModelTest {
    private val database = inMemoryDatabase()
    private val dao = database.noteDao()
    private val clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)
    private var syncs = 0
    private val trigger = SyncTrigger { syncs++ }
    private var nextRemoteId = 1L

    private companion object {
        val WAIT = 5.seconds
        const val POLL = 10L
    }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    /**
     * The models of the test. Their view-model scope runs on Main and observes Room: it must be
     * over, completions included, before Main is reset and the database closed. Otherwise a
     * Room thread finishes it later, finds Main gone, and the exception is reported against
     * whichever test runs next (the intermittent CI failure).
     */
    private val models = mutableListOf<EditorViewModel>()

    @AfterEach
    fun tearDown() {
        runBlocking { models.forEach { it.viewModelScope.coroutineContext[Job]!!.cancelAndJoin() } }
        Dispatchers.resetMain()
        database.close()
    }

    private val writer = spyk(NoteWriter(dao, clock) { "New note" })

    private fun TestScope.model() = EditorViewModel(
        dao,
        writer,
        NoteActions(dao),
        ObserveFolders(dao),
        trigger,
        backgroundScope
    ).also { models += it }

    private suspend fun synced(
        content: String = "hello",
        readonly: Boolean = false,
        category: String = "Work",
        title: String = "t"
    ) = dao.insert(
        NoteEntity(
            id = nextRemoteId++,
            etag = "e",
            readonly = readonly,
            modified = 100,
            title = title,
            category = category,
            content = content,
            syncState = SyncState.SYNCED,
            lastSyncedEtag = "e"
        )
    )

    /** What a keystroke does: change the text, then let the snapshot observers hear about it. */
    private fun TestScope.typeInto(model: EditorViewModel, value: String) {
        model.type(value)
        runCurrent()
    }

    private fun EditorViewModel.type(value: String) {
        text.edit { replace(0, length, value) }
        Snapshot.sendApplyNotifications()
    }

    /** What typing in the title line does. */
    private fun TestScope.typeTitle(model: EditorViewModel, value: String) {
        model.title.edit { replace(0, length, value) }
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    private suspend fun all() = dao.observeAll().first()

    /** Opens a note and waits until the (database) load is over. */
    private suspend fun EditorViewModel.openAndWait(id: Long, category: String = "") {
        open(id, category)
        state.first { it.loaded }
    }

    /**
     * Runs queued coroutines until the model's scope has nothing left to do: every save, deletion
     * and sync request it launched is over. Waits for that state (not for a fixed time), so a slow
     * runner only makes it take longer. Needs the autosave stopped: call it after `close()`.
     */
    private suspend fun TestScope.settle() {
        val deadline = System.nanoTime() + WAIT.inWholeNanoseconds
        while (true) {
            runCurrent()
            if (backgroundScope.coroutineContext[Job]!!.children.none { it.isActive }) return
            check(System.nanoTime() < deadline) { "the model's coroutines did not finish" }
            withContext(Dispatchers.Default) { delay(POLL) }
        }
    }

    /** Settles until [read] gives [expected] (or the wait is over), then asserts it. */
    private suspend fun <T> TestScope.eventually(expected: T, read: suspend () -> T) {
        val deadline = System.nanoTime() + WAIT.inWholeNanoseconds
        while (System.nanoTime() < deadline) {
            runCurrent()
            if (read() == expected) break
            withContext(Dispatchers.Default) { delay(POLL) }
        }
        assertEquals(expected, read())
    }

    @Test
    fun `opening a note shows its text without any history`() = runTest {
        val id = synced("# Title\nbody")
        val model = model()
        model.openAndWait(id)
        assertEquals("# Title\nbody", model.text.text.toString())
        assertEquals("t", model.title.text.toString())
        assertFalse(model.text.undoState.canUndo)
        assertFalse(model.title.undoState.canUndo)
        val state = model.state.first { it.loaded }
        assertEquals("Work", state.category)
        assertFalse(state.readOnly)
    }

    @Test
    fun `opening the same note again keeps the text being edited`() = runTest {
        val id = synced("hello")
        val model = model()
        model.openAndWait(id)
        typeInto(model, "hello world")
        model.openAndWait(id)
        assertEquals("hello world", model.text.text.toString())
    }

    @Test
    fun `a note that is gone is reported as missing`() = runTest {
        val model = model()
        model.openAndWait(404)
        assertTrue(model.state.first { it.loaded }.missing)
        val deleted = synced().also {
            dao.update(dao.get(it)!!.copy(syncState = SyncState.DELETED))
        }
        model.openAndWait(deleted)
        assertTrue(model.state.first { it.loaded }.missing)
    }

    @Test
    fun `typing is saved one second after the last keystroke`() = runTest {
        val id = synced("hello")
        val model = model()
        model.openAndWait(id)
        typeInto(model, "hello w")
        advanceTimeBy(900)
        // Not a suspending read: waiting on the database would let the test clock run on.
        coVerify(exactly = 0) { writer.update(any(), any(), any()) }
        typeInto(model, "hello world")
        advanceTimeBy(900)
        coVerify(exactly = 0) { writer.update(any(), any(), any()) }
        advanceTimeBy(200)
        eventually("hello world") { dao.get(id)!!.content }
        coVerify(exactly = 1) { writer.update(id, "t", "hello world") }
        assertEquals(SyncState.DIRTY, dao.get(id)!!.syncState)
        model.close()
        settle()
    }

    @Test
    fun `closing saves at once and asks for a sync`() = runTest {
        val id = synced("hello")
        val model = model()
        model.openAndWait(id)
        typeInto(model, "hello again")
        model.close()
        settle()
        assertEquals("hello again", dao.get(id)!!.content)
        assertEquals(1, syncs)
    }

    @Test
    fun `closing without changes writes nothing and does not sync`() = runTest {
        val id = synced("hello")
        val model = model()
        model.openAndWait(id)
        model.close()
        settle()
        assertEquals(100, dao.get(id)!!.modified)
        assertEquals(SyncState.SYNCED, dao.get(id)!!.syncState)
        assertEquals(0, syncs)
    }

    @Test
    fun `a new note is created in the current folder once it has text`() = runTest {
        val model = model()
        model.openAndWait(NEW_NOTE_ID, category = "Work/Ideas")
        assertEquals(emptyList<NoteEntity>(), all())
        typeInto(model, "# Idea\nsome text")
        advanceTimeBy(1_100)
        eventually(1) { all().size }
        val note = all().single()
        assertEquals("Work/Ideas", note.category)
        assertEquals("Idea", note.title)
        // The user sees the title the note was given.
        assertEquals("Idea", model.title.text.toString())
        assertEquals(SyncState.NEW, note.syncState)
        typeInto(model, "# Idea\nsome more text")
        advanceTimeBy(1_100)
        eventually("# Idea\nsome more text") { all().single().content }
        model.close()
        settle()
    }

    @Test
    fun `a new note keeps the title derived at creation when the body changes later`() = runTest {
        val model = model()
        model.openAndWait(NEW_NOTE_ID)
        typeInto(model, "First idea\nmore")
        advanceTimeBy(1_100)
        eventually(1) { all().size }
        typeInto(model, "Rewritten opening\nmore")
        advanceTimeBy(1_100)
        eventually("Rewritten opening\nmore") { all().single().content }
        assertEquals("First idea", all().single().title)
        assertEquals("First idea", model.title.text.toString())
        model.close()
        settle()
    }

    @Test
    fun `a title typed on a new note is used as is and saved with it`() = runTest {
        val model = model()
        model.openAndWait(NEW_NOTE_ID)
        typeTitle(model, "Groceries")
        typeInto(model, "Milk\neggs")
        model.close()
        settle()
        val note = all().single()
        assertEquals("Groceries", note.title)
        assertEquals("Milk\neggs", note.content)
        assertEquals("Groceries", model.title.text.toString())
    }

    @Test
    fun `a new note with a title but no body is created`() = runTest {
        val model = model()
        model.openAndWait(NEW_NOTE_ID)
        typeTitle(model, "Ideas")
        model.close()
        settle()
        assertEquals("Ideas", all().single().title)
        assertEquals(1, syncs)
    }

    @Test
    fun `a new note whose body has no words gets the default title`() = runTest {
        val model = model()
        model.openAndWait(NEW_NOTE_ID)
        typeInto(model, "-\n#")
        model.close()
        settle()
        assertEquals("New note", all().single().title)
    }

    @Test
    fun `renaming an existing note saves the title only, after the debounce`() = runTest {
        val id = synced("body", title = "Old")
        val model = model()
        model.openAndWait(id)
        typeTitle(model, "Renamed")
        advanceTimeBy(900)
        coVerify(exactly = 0) { writer.update(any(), any(), any()) }
        advanceTimeBy(200)
        eventually("Renamed") { dao.get(id)!!.title }
        val note = dao.get(id)!!
        assertEquals("body", note.content)
        assertEquals(SyncState.DIRTY, note.syncState)
        model.close()
        settle()
        assertEquals(1, syncs)
    }

    @Test
    fun `closing while the autosave is writing still asks for a sync`() = runTest {
        val id = synced("body", title = "Old")
        val model = model()
        model.openAndWait(id)
        val committed = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        // The write reaches the database, then the save is held up (as a slow disk would).
        coEvery { writer.update(any(), any(), any()) } coAnswers {
            val wrote = NoteWriter(dao, clock) {
                "New note"
            }.update(firstArg(), secondArg(), thirdArg())
            committed.complete(Unit)
            release.await()
            wrote
        }
        typeTitle(model, "Renamed")
        advanceTimeBy(1_100)
        committed.await()
        model.close() // cancels the autosave in the middle of its write
        release.complete(Unit)
        settle()
        assertEquals("Renamed", dao.get(id)!!.title)
        assertEquals(1, syncs)
    }

    @Test
    fun `editing the body of an existing note never re-derives its title`() = runTest {
        val id = synced("Old first line\nrest", title = "Chosen")
        val model = model()
        model.openAndWait(id)
        typeInto(model, "New first line\nrest")
        model.close()
        settle()
        assertEquals("Chosen", dao.get(id)!!.title)
        assertEquals("New first line\nrest", dao.get(id)!!.content)
    }

    @Test
    fun `a title cleared on an existing note is not stored as blank`() = runTest {
        val id = synced("body", title = "Keep me")
        val model = model()
        model.openAndWait(id)
        typeTitle(model, "")
        model.close()
        settle()
        assertEquals("Keep me", dao.get(id)!!.title)
        assertEquals(SyncState.SYNCED, dao.get(id)!!.syncState)
    }

    @Test
    fun `the title has its own undo history`() = runTest {
        val id = synced("body", title = "Old")
        val model = model()
        model.openAndWait(id)
        typeTitle(model, "Renamed")
        assertTrue(model.title.undoState.canUndo)
        model.title.undoState.undo()
        assertEquals("Old", model.title.text.toString())
        assertFalse(model.text.undoState.canUndo)
        model.close()
        settle()
    }

    @Test
    fun `a new note whose title the user typed survives an emptied body`() = runTest {
        val model = model()
        model.openAndWait(NEW_NOTE_ID)
        typeTitle(model, "Mine")
        typeInto(model, "oops")
        advanceTimeBy(1_100)
        eventually(1) { all().size }
        typeInto(model, "")
        model.close()
        settle()
        assertEquals("Mine", all().single().title)
    }

    @Test
    fun `read-only notes cannot be retitled`() = runTest {
        val id = synced("fixed", readonly = true, title = "Locked")
        val model = model()
        model.openAndWait(id)
        typeTitle(model, "Changed")
        model.close()
        settle()
        assertEquals("Locked", dao.get(id)!!.title)
        assertEquals(0, syncs)
    }

    @Test
    fun `a new note left blank is discarded`() = runTest {
        val model = model()
        model.openAndWait(NEW_NOTE_ID)
        typeInto(model, "  \n ")
        model.close()
        settle()
        assertEquals(emptyList<NoteEntity>(), all())
        assertEquals(0, syncs)
    }

    @Test
    fun `a new note emptied again before leaving is deleted`() = runTest {
        val model = model()
        model.openAndWait(NEW_NOTE_ID)
        typeInto(model, "oops")
        advanceTimeBy(1_100)
        eventually(1) { all().size }
        typeInto(model, "")
        model.close()
        settle()
        assertEquals(emptyList<NoteEntity>(), all())
    }

    @Test
    fun `an existing note emptied by the user stays`() = runTest {
        val id = synced("hello")
        val model = model()
        model.openAndWait(id)
        typeInto(model, "")
        model.close()
        settle()
        assertEquals("", dao.get(id)!!.content)
        assertEquals(SyncState.DIRTY, dao.get(id)!!.syncState)
    }

    @Test
    fun `read-only notes cannot be changed`() = runTest {
        val id = synced("fixed", readonly = true)
        val model = model()
        model.openAndWait(id)
        assertTrue(model.state.first { it.loaded }.readOnly)
        model.format(FormatAction.Inline(InlineStyle.Bold))
        model.toggleFavorite()
        model.move("Other")
        typeInto(model, "changed")
        model.close()
        settle()
        val note = dao.get(id)!!
        assertEquals("fixed", note.content)
        assertFalse(note.favorite)
        assertEquals("Work", note.category)
        assertEquals(0, syncs)
    }

    @Test
    fun `formatting edits the selection as one undoable step`() = runTest {
        val id = synced("hello world")
        val model = model()
        model.openAndWait(id)
        model.text.edit { selection = TextRange(6, 11) }
        model.format(FormatAction.Inline(InlineStyle.Bold))
        assertEquals("hello **world**", model.text.text.toString())
        assertEquals(TextRange(8, 13), model.text.selection)
        assertTrue(model.text.undoState.canUndo)
        model.text.undoState.undo()
        assertEquals("hello world", model.text.text.toString())
        model.text.undoState.redo()
        assertEquals("hello **world**", model.text.text.toString())
        model.close()
        settle()
    }

    @Test
    fun `block formatting works on the caret line`() = runTest {
        val id = synced("one\ntwo")
        val model = model()
        model.openAndWait(id)
        model.text.edit { selection = TextRange(5) }
        model.format(FormatAction.Block(BlockKind.Checklist))
        assertEquals("one\n- [ ] two", model.text.text.toString())
        model.close()
        settle()
    }

    @Test
    fun `favorite and move of a stored note go through the list's actions`() = runTest {
        val id = synced("hello")
        val model = model()
        model.openAndWait(id)
        model.toggleFavorite()
        model.move(" Home / Kids ")
        eventually(true) { dao.get(id)!!.favorite && dao.get(id)!!.category == "Home/Kids" }
        assertEquals(SyncState.DIRTY, dao.get(id)!!.syncState)
        val state = model.state.first { it.favorite }
        assertEquals("Home/Kids", state.category)
        model.toggleFavorite()
        eventually(false) { dao.get(id)!!.favorite }
        model.close()
        settle()
    }

    @Test
    fun `favorite and folder chosen before the first save apply to the new note`() = runTest {
        val model = model()
        model.openAndWait(NEW_NOTE_ID, category = "Work")
        model.toggleFavorite()
        model.move("Home")
        typeInto(model, "text")
        model.close()
        settle()
        val note = all().single()
        assertTrue(note.favorite)
        assertEquals("Home", note.category)
        assertEquals(1, syncs)
    }

    @Test
    fun `deleting saves the text first and hands the note to the list`() = runTest {
        val id = synced("hello")
        val model = model()
        model.openAndWait(id)
        typeInto(model, "hello edited")
        assertEquals(id, model.closeForDelete())
        assertEquals("hello edited", dao.get(id)!!.content)
        assertEquals(0, syncs)
        assertNull(model.closeForDelete())
    }

    @Test
    fun `deleting a new note that was never created hands over nothing`() = runTest {
        val model = model()
        model.openAndWait(NEW_NOTE_ID)
        assertNull(model.closeForDelete())
        assertEquals(emptyList<NoteEntity>(), all())
    }

    @Test
    fun `a session that ends when another note opens is saved`() = runTest {
        val first = synced("one")
        val second = synced("two")
        val model = model()
        model.openAndWait(first)
        typeInto(model, "one edited")
        model.openAndWait(second)
        assertEquals("two", model.text.text.toString())
        assertFalse(model.text.undoState.canUndo)
        model.close()
        settle()
        assertEquals("one edited", dao.get(first)!!.content)
    }

    @Test
    fun `saving now persists without waiting for the debounce`() = runTest {
        val id = synced("hello")
        val model = model()
        model.openAndWait(id)
        typeInto(model, "hello!")
        model.saveInBackground()
        eventually("hello!") { dao.get(id)!!.content }
        model.close()
        settle()
        model.saveNow()
    }
}
