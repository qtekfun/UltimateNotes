// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.list

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** The delay runs on the test scheduler's virtual clock: no test waits for real time. */
@OptIn(ExperimentalCoroutinesApi::class)
class UndoableDeletesTest {
    private val window = 5.seconds
    private val committed = mutableListOf<List<Long>>()

    private fun TestScope.deletes(fail: Boolean = false) =
        UndoableDeletes(backgroundScope, window) { ids ->
            if (fail) error("database is gone")
            committed += ids.sorted()
        }

    @Test
    fun `scheduled notes are hidden at once but not committed`() = runTest {
        val deletes = deletes()
        deletes.schedule(listOf(1, 2))
        assertEquals(setOf(1L, 2L), deletes.hidden.value)
        assertEquals(emptyList<List<Long>>(), committed)
    }

    @Test
    fun `the deletion is committed only after the window`() = runTest {
        val deletes = deletes()
        deletes.schedule(listOf(1, 2))
        advanceTimeBy(window - 1.seconds)
        runCurrent()
        assertEquals(emptyList<List<Long>>(), committed)
        assertEquals(setOf(1L, 2L), deletes.hidden.value)

        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals(listOf(listOf(1L, 2L)), committed)
        assertEquals(emptySet<Long>(), deletes.hidden.value)
    }

    @Test
    fun `undo inside the window brings the notes back and nothing is ever committed`() = runTest {
        val deletes = deletes()
        deletes.schedule(listOf(1))
        advanceTimeBy(window - 1.seconds)
        assertTrue(deletes.undo())
        assertEquals(emptySet<Long>(), deletes.hidden.value)

        advanceTimeBy(window * 2)
        runCurrent()
        assertEquals(emptyList<List<Long>>(), committed)
    }

    @Test
    fun `undo with nothing pending does nothing`() = runTest {
        val deletes = deletes()
        assertFalse(deletes.undo())
        deletes.schedule(listOf(1))
        advanceTimeBy(window)
        runCurrent()
        assertFalse(deletes.undo())
        assertEquals(listOf(listOf(1L)), committed)
    }

    @Test
    fun `scheduling nothing starts no window`() = runTest {
        val deletes = deletes()
        deletes.schedule(emptyList())
        advanceTimeBy(window * 2)
        runCurrent()
        assertEquals(emptyList<List<Long>>(), committed)
        assertEquals(emptySet<Long>(), deletes.hidden.value)
    }

    @Test
    fun `a new deletion commits the previous one and gets its own full window`() = runTest {
        val deletes = deletes()
        deletes.schedule(listOf(1))
        advanceTimeBy(3.seconds)
        deletes.schedule(listOf(2))
        assertEquals(listOf(listOf(1L)), committed)
        assertEquals(setOf(2L), deletes.hidden.value)

        // The first timer would fire here; it must not commit the second deletion early.
        advanceTimeBy(window - 1.seconds)
        runCurrent()
        assertEquals(listOf(listOf(1L)), committed)
        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals(listOf(listOf(1L), listOf(2L)), committed)
    }

    @Test
    fun `undo only brings back the latest deletion`() = runTest {
        val deletes = deletes()
        deletes.schedule(listOf(1))
        deletes.schedule(listOf(2))
        assertTrue(deletes.undo())
        assertEquals(listOf(listOf(1L)), committed)
        assertEquals(emptySet<Long>(), deletes.hidden.value)
    }

    @Test
    fun `flush commits right away and the timer then has nothing left to do`() = runTest {
        val deletes = deletes()
        deletes.schedule(listOf(1, 2))
        deletes.flush()
        assertEquals(listOf(listOf(1L, 2L)), committed)
        assertEquals(emptySet<Long>(), deletes.hidden.value)

        advanceTimeBy(window * 2)
        runCurrent()
        assertEquals(1, committed.size)
    }

    @Test
    fun `flush with nothing pending commits nothing`() = runTest {
        val deletes = deletes()
        deletes.flush()
        assertEquals(emptyList<List<Long>>(), committed)
    }

    @Test
    fun `a failed commit still shows the notes again`() = runTest {
        val deletes = deletes(fail = true)
        deletes.schedule(listOf(1))
        assertThrows<IllegalStateException> { deletes.flush() }
        assertEquals(emptySet<Long>(), deletes.hidden.value)
    }
}
