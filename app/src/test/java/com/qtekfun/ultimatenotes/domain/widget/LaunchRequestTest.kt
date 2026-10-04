// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LaunchRequestTest {
    @Test
    fun `a new note request wins over a note id`() {
        assertEquals(LaunchRequest.NewNote, LaunchRequest.from(newNote = true, noteId = 5))
    }

    @Test
    fun `a positive note id opens that note`() {
        assertEquals(LaunchRequest.OpenNote(5), LaunchRequest.from(newNote = false, noteId = 5))
    }

    @Test
    fun `missing, zero or negative ids are not requests`() {
        assertNull(LaunchRequest.from(false, null))
        assertNull(LaunchRequest.from(false, 0))
        assertNull(LaunchRequest.from(false, -1))
    }
}
