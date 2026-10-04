// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.editor

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.text.TextRange
import app.cash.turbine.test
import com.qtekfun.ultimatenotes.domain.link.LinkOpener
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorLinksTest {
    private val opened = mutableListOf<String>()
    private val links = EditorLinks(LinkOpener { opened += it })
    private val note = "see [docs](https://example.org) and [bad](javascript:alert(1)) end"

    @Test
    fun `the caret in a link gives its destination, a selection or plain text gives none`() {
        val inside = note.indexOf("docs")
        assertEquals("https://example.org", links.linkAtCaret(note, TextRange(inside)))
        assertNull(links.linkAtCaret(note, TextRange(0)))
        assertNull(links.linkAtCaret(note, TextRange(inside, inside + 2)))
        assertNull(links.linkAtCaret(note, TextRange(note.indexOf("bad"))))
    }

    @Test
    fun `a tap opens the link only in a read-only note`() {
        val index = note.indexOf("docs")
        assertFalse(links.tapped(note, index, readOnly = false))
        assertEquals(emptyList<String>(), opened)
        assertTrue(links.tapped(note, index, readOnly = true))
        assertEquals(listOf("https://example.org"), opened)
    }

    @Test
    fun `a tap off a link, off the text or on a refused link opens nothing`() {
        assertFalse(links.tapped(note, 0, readOnly = true))
        assertFalse(links.tapped(note, null, readOnly = true))
        assertFalse(links.tapped(note, note.indexOf("bad"), readOnly = true))
        assertEquals(emptyList<String>(), opened)
    }

    @Test
    fun `the button action opens through the opener`() {
        links.open("tel:+34600")
        assertEquals(listOf("tel:+34600"), opened)
    }

    @Test
    fun `the caret link follows the text and the selection`() = runTest {
        val state = TextFieldState(note, TextRange(0))
        links.caretLinks(state, UnconfinedTestDispatcher(testScheduler)).test {
            assertNull(awaitItem())
            state.edit { selection = TextRange(note.indexOf("docs")) }
            Snapshot.sendApplyNotifications()
            assertEquals("https://example.org", awaitItem())
            state.edit { selection = TextRange(note.indexOf("bad")) }
            Snapshot.sendApplyNotifications()
            assertNull(awaitItem())
            state.edit {
                replace(0, length, "[x](mailto:a@b.org)")
                selection = TextRange(1)
            }
            Snapshot.sendApplyNotifications()
            assertEquals("mailto:a@b.org", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
