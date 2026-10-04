// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LineTest {
    @Test
    fun `all three line endings end a line`() {
        assertEquals(Line(0, 1, 2), lineAt("a\nb", 0))
        assertEquals(Line(0, 1, 3), lineAt("a\r\nb", 0))
        assertEquals(Line(0, 1, 2), lineAt("a\rb", 0))
        assertEquals(Line(0, 1, 2), lineAt("a\r", 0))
    }

    @Test
    fun `the last line has no terminator`() {
        assertEquals(Line(2, 3, 3), lineAt("a\nb", 3))
        assertEquals(Line(2, 2, 2), lineAt("a\n", 2))
    }

    @Test
    fun `lines between covers the touched lines`() {
        val text = "a\nb\nc"
        assertEquals(listOf(Line(0, 1, 2)), linesBetween(text, 0, 0))
        assertEquals(listOf(Line(0, 1, 2), Line(2, 3, 4)), linesBetween(text, 0, 3))
        assertEquals(listOf(Line(0, 1, 2)), linesBetween(text, 0, 2))
        assertEquals(listOf(Line(0, 1, 2), Line(2, 3, 4), Line(4, 5, 5)), linesBetween(text, 1, 5))
        assertEquals(listOf(Line(4, 5, 5)), linesBetween(text, 5, 5))
    }
}
