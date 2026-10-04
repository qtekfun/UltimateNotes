// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.checklist

import com.qtekfun.ultimatenotes.domain.TextRange
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ChecklistParserTest {
    @ParameterizedTest
    @ValueSource(
        strings = [
            "- [ ] a", "* [ ] a", "+ [ ] a", "  - [ ] a", "\t- [ ] a", "-   [ ] a",
            "- [ ]", "- [ ]\t"
        ]
    )
    fun `detects unchecked items with any marker, indentation and spacing`(line: String) {
        val item = ChecklistParser.parseAt(line, 0)!!
        assertEquals(false, item.checked)
        assertEquals("[ ]", line.substring(item.box.start, item.box.end))
    }

    @ParameterizedTest
    @ValueSource(strings = ["- [x] a", "- [X] a", "* [x]", "    + [X] a"])
    fun `detects checked items in both cases`(line: String) {
        val item = ChecklistParser.parseAt(line, 0)!!
        assertEquals(true, item.checked)
        assertEquals(line.indexOf('[') + 1, item.stateOffset)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "", "plain", "- a", "- [x]a", "-[ ] a", "- [] a", "- [  ] a", "- [y] a", "1. [ ] a",
            "x - [ ] a", "[ ] a", "-- [ ] a", ">- a"
        ]
    )
    fun `rejects lines that are not checklist items`(line: String) {
        assertNull(ChecklistParser.parseAt(line, 0))
    }

    @Test
    fun `parseAt can start at a list marker in the middle of a line`() {
        val text = "- > - [x] quoted"
        assertNull(ChecklistParser.parseAt(text, 0))
        assertEquals(TextRange(6, 9), ChecklistParser.parseAt(text, 4)!!.box)
        assertEquals("- > - [ ] quoted", ChecklistParser.toggle(text, TextRange(6, 9)))
    }

    @Test
    fun `parse finds items on every line ending style and keeps their absolute offsets`() {
        val text = "- [ ] a\r\ntext\n* [x] b\r- [ ] c\r\n\r\n  + [X] d"
        val items = ChecklistParser.parse(text)
        assertEquals(listOf(false, true, false, true), items.map { it.checked })
        items.forEach {
            assertEquals(
                true,
                text.substring(it.box.start, it.box.end).matches(Regex("""\[[ xX]]"""))
            )
        }
        assertEquals(listOf(2, 16, 24, 37), items.map { it.box.start })
    }

    @Test
    fun `parse of text without checklists or empty text is empty`() {
        assertEquals(emptyList<ChecklistItem>(), ChecklistParser.parse(""))
        assertEquals(emptyList<ChecklistItem>(), ChecklistParser.parse("- a\n\n1. b\n"))
    }

    @Test
    fun `toggle flips the box and changes exactly one character`() {
        assertEquals("- [x] milk", ChecklistParser.toggleAtOffset("- [ ] milk", 0))
        assertEquals("- [ ] milk", ChecklistParser.toggleAtOffset("- [x] milk", 5))
        assertEquals("- [ ] milk", ChecklistParser.toggleAtOffset("- [X] milk", 10))
    }

    @Test
    fun `toggleAtOffset only touches the line containing the offset`() {
        val text = "- [ ] a\r\n- [ ] b\r\n- [ ] c"
        assertEquals("- [ ] a\r\n- [x] b\r\n- [ ] c", ChecklistParser.toggleAtOffset(text, 12))
        assertEquals(
            "- [ ] a\r\n- [ ] b\r\n- [x] c",
            ChecklistParser.toggleAtOffset(text, text.length)
        )
        assertEquals("- [x] a\r\n- [ ] b\r\n- [ ] c", ChecklistParser.toggleAtOffset(text, 0))
    }

    @Test
    fun `toggleAtOffset returns null on non checklist lines and inside a CRLF terminator`() {
        val text = "title\r\n- [ ] a\rplain\n"
        assertNull(ChecklistParser.toggleAtOffset(text, 2))
        assertNull(ChecklistParser.toggleAtOffset(text, 6))
        assertNull(ChecklistParser.toggleAtOffset(text, text.indexOf("plain") + 3))
        assertNull(ChecklistParser.toggleAtOffset(text, text.length))
        assertNull(ChecklistParser.toggleAtOffset("", 0))
    }

    @Test
    fun `toggleLine addresses lines by number across mixed line endings`() {
        val text = "a\r\n- [ ] b\n- [x] c\r- [ ] d"
        assertNull(ChecklistParser.toggleLine(text, 0))
        assertEquals("a\r\n- [x] b\n- [x] c\r- [ ] d", ChecklistParser.toggleLine(text, 1))
        assertEquals("a\r\n- [ ] b\n- [ ] c\r- [ ] d", ChecklistParser.toggleLine(text, 2))
        assertEquals("a\r\n- [ ] b\n- [x] c\r- [x] d", ChecklistParser.toggleLine(text, 3))
        assertNull(ChecklistParser.toggleLine(text, 4))
        assertNull(ChecklistParser.toggleLine("", 0))
    }

    @Test
    fun `quoted checklist lines are checklists too`() {
        assertEquals("> - [x] a", ChecklistParser.toggleAtOffset("> - [ ] a", 3))
        assertEquals(TextRange(7, 10), ChecklistParser.parse(" > > - [ ] a").single().box)
    }

    @Test
    fun `toggle by box only accepts a real checklist box`() {
        val text = "- [ ] a [b] [x]"
        assertEquals("- [x] a [b] [x]", ChecklistParser.toggle(text, TextRange(2, 5)))
        assertEquals("- [ ] a [b] [ ]", ChecklistParser.toggle(text, TextRange(12, 15)))
        assertThrows(IllegalArgumentException::class.java) {
            ChecklistParser.toggle(text, TextRange(2, 4))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChecklistParser.toggle(text, TextRange(13, 16))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChecklistParser.toggle(text, TextRange(8, 11))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChecklistParser.toggle(text, TextRange(0, 3))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChecklistParser.toggle("[ a]", TextRange(0, 3))
        }
    }

    @Test
    fun `invalid arguments are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ChecklistParser.toggleLine("- [ ] a", -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChecklistParser.toggleAtOffset("- [ ] a", -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChecklistParser.toggleAtOffset("- [ ] a", 8)
        }
    }

    @Test
    fun `random texts toggle exactly one character and toggling twice restores the text`() {
        val random = Random(SEED)
        val pieces =
            listOf(
                "- [ ] ", "- [x] ", "* [X] ", "+ [ ]", "  ", "\t", "\r\n", "\n", "\r",
                "word ", "😀", "[ ]", "- ", "> "
            )
        repeat(ITERATIONS) {
            val text = List(random.nextInt(MAX_PIECES)) { pieces.random(random) }.joinToString("")
            for (offset in 0..text.length) {
                val toggled = ChecklistParser.toggleAtOffset(text, offset) ?: continue
                val changed = text.indices.filter { text[it] != toggled[it] }
                assertEquals(text.length, toggled.length)
                assertEquals(1, changed.size)
                val stateOffsets = ChecklistParser.parse(text).map { it.stateOffset }
                assertEquals(true, changed.single() in stateOffsets)
                // 'X' normalizes to 'x' after a round trip; every other state is restored exactly.
                val expected = text.replaceRange(
                    changed.single(),
                    changed.single() + 1,
                    if (text[changed.single()] ==
                        'X'
                    ) {
                        "x"
                    } else {
                        text[changed.single()].toString()
                    }
                )
                assertEquals(expected, ChecklistParser.toggleAtOffset(toggled, offset))
            }
        }
    }

    private companion object {
        const val SEED = 20_261_004
        const val ITERATIONS = 300
        const val MAX_PIECES = 25
    }
}
