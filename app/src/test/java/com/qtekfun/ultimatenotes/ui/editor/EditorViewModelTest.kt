// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.text.TextRange
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
import io.mockk.coVerify
import io.mockk.spyk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
        const val SETTLE_ROUNDS = 5
    }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    private val writer = spyk(NoteWriter(dao, clock))

    private fun TestScope.model() = EditorViewModel(
        dao,
        writer,
        NoteActions(dao),
        ObserveFolders(dao),
        trigger,
        backgroundScope
    )

    private suspend fun synced(
        content: String = "hello",
        readonly: Boolean = false,
        category: String = "Work"
    ) = dao.insert(
        NoteEntity(
            id = nextRemoteId++,
            etag = "e",
            readonly = readonly,
            modified = 100,
            title = "t",
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

    private suspend fun all() = dao.observeAll().first()

    /** Opens a note and waits until the (database) load is over. */
    private suspend fun EditorViewModel.openAndWait(id: Long, category: String = "") {
        open(id, category)
        state.first { it.loaded }
    }

    /** Lets queued coroutines run and, in real time, database threads finish. */
    private suspend fun TestScope.settle() {
        repeat(SETTLE_ROUNDS) {
            runCurrent()
            withContext(Dispatchers.Default) { delay(POLL) }
        }
        runCurrent()
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
        assertFalse(model.text.undoState.canUndo)
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
        coVerify(exactly = 0) { writer.update(any(), any()) }
        typeInto(model, "hello world")
        advanceTimeBy(900)
        coVerify(exactly = 0) { writer.update(any(), any()) }
        advanceTimeBy(200)
        eventually("hello world") { dao.get(id)!!.content }
        coVerify(exactly = 1) { writer.update(id, "hello world") }
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
        assertEquals(SyncState.NEW, note.syncState)
        typeInto(model, "# Idea\nsome more text")
        advanceTimeBy(1_100)
        eventually("# Idea\nsome more text") { all().single().content }
        model.close()
        settle()
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
        settle()
        assertEquals("one edited", dao.get(first)!!.content)
        assertEquals("two", model.text.text.toString())
        assertFalse(model.text.undoState.canUndo)
        model.close()
        settle()
    }

    @Test
    fun `saving now persists without waiting for the debounce`() = runTest {
        val id = synced("hello")
        val model = model()
        model.openAndWait(id)
        typeInto(model, "hello!")
        model.saveInBackground()
        settle()
        assertEquals("hello!", dao.get(id)!!.content)
        model.close()
        settle()
        model.saveNow()
    }
}
