// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

import com.qtekfun.ultimatenotes.domain.TextRange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ListContinuationTest {
    @Test
    fun `enter on a bullet starts the next bullet with the same marker`() {
        assertEquals("- a\n- |", enter("- a|"))
        assertEquals("* a\n* |", enter("* a|"))
        assertEquals("+ a\n+ |", enter("+ a|"))
    }

    @Test
    fun `enter in the middle of an item splits it`() {
        assertEquals("- a\n- |b", enter("- a|b"))
        assertEquals("- \n- |abc", enter("- |abc"))
    }

    @Test
    fun `enter on a checklist starts an unticked item`() {
        assertEquals("- [ ] a\n- [ ] |", enter("- [ ] a|"))
        assertEquals("- [x] a\n- [ ] |", enter("- [x] a|"))
        assertEquals("* [X] a\n* [ ] |", enter("* [X] a|"))
    }

    @Test
    fun `enter on a numbered item counts on`() {
        assertEquals("1. a\n2. |", enter("1. a|"))
        assertEquals("9) a\n10) |", enter("9) a|"))
        assertEquals("999999999. a\n1000000000. |", enter("999999999. a|"))
    }

    @Test
    fun `indentation is kept`() {
        assertEquals("  - a\n  - |", enter("  - a|"))
        assertEquals("\t1. a\n\t2. |", enter("\t1. a|"))
    }

    @Test
    fun `enter on an empty item ends the list`() {
        assertEquals("|", enter("- |"))
        assertEquals("|", enter("-|"))
        assertEquals("|", enter("- [ ] |"))
        assertEquals("|", enter("- [x] |"))
        assertEquals("|", enter("1. |"))
        assertEquals("|", enter("  - |"))
        assertEquals("|", enter("-   |"))
    }

    @Test
    fun `ending a list leaves the other lines alone`() {
        assertEquals("a\n- b\n|\nc", enter("a\n- b\n- |\nc"))
        assertEquals("- a\n|\n- c", enter("- a\n- |\n- c"))
    }

    @Test
    fun `enter on a quote continues the quote`() {
        assertEquals("> a\n> |", enter("> a|"))
        assertEquals("> > a\n> > |", enter("> > a|"))
        assertEquals("> a\n> |b", enter("> a|b"))
    }

    @Test
    fun `enter on an empty quote line ends the quote`() {
        assertEquals("|", enter("> |"))
        assertEquals("|", enter("> > |"))
    }

    @Test
    fun `lists inside quotes keep the quote`() {
        assertEquals("> - a\n> - |", enter("> - a|"))
        assertEquals("> |", enter("> - |"))
        assertEquals("> 1. a\n> 2. |", enter("> 1. a|"))
    }

    @Test
    fun `plain lines are left to the normal newline`() {
        assertNull(enter("plain|"))
        assertNull(enter("|"))
        assertNull(enter("  indented|"))
        assertNull(enter("**bold**|"))
        assertNull(enter("-5|"))
        assertNull(enter("3.14|"))
        assertNull(enter("# heading|"))
    }

    @Test
    fun `enter inside the marker or with a selection is left alone`() {
        assertNull(enter("-| a"))
        assertNull(enter("|- a"))
        assertNull(enter("1|. a"))
        assertNull(enter("‹- a›"))
    }

    @Test
    fun `works in the middle of a document`() {
        assertEquals("title\n\n- a\n- |\nrest", enter("title\n\n- a|\nrest"))
        assertEquals("- a\n- |", enter("- a|"))
    }

    @Test
    fun `new lines follow the document's line ending`() {
        val result = continueList("- a\r\nb", TextRange(3, 3))
        assertEquals(EditResult("- a\r\n- \r\nb", TextRange(7, 7)), result)
        assertEquals("- a\n- |\nb", enter("- a|\nb"))
    }

    @Test
    fun `only the new marker is inserted`() {
        val text = "- a"
        val result = continueList(text, TextRange(3, 3))!!
        assertEquals(TextEdit(TextRange(3, 3), "\n- ", TextRange(6, 6)), result.toEdit(text))
    }

    @Test
    fun `rejects a selection outside the text`() {
        assertThrows(IllegalArgumentException::class.java) { continueList("a", TextRange(2, 2)) }
    }
}
