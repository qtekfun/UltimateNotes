// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

import com.qtekfun.ultimatenotes.domain.TextRange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class BlockFormatTest {
    private val h1 = BlockKind.Heading(1)
    private val h2 = BlockKind.Heading(2)

    @Test
    fun `sets each kind on a plain line`() {
        assertEquals("# Title|", block("Title|", h1))
        assertEquals("## Title|", block("Title|", h2))
        assertEquals("> quote|", block("quote|", BlockKind.Quote))
        assertEquals("- item|", block("item|", BlockKind.Bullet))
        assertEquals("1. item|", block("item|", BlockKind.Numbered))
        assertEquals("- [ ] item|", block("item|", BlockKind.Checklist))
    }

    @Test
    fun `the caret keeps its place in the text`() {
        assertEquals("# Ti|tle", block("Ti|tle", h1))
        assertEquals("- [ ] |item", block("|item", BlockKind.Checklist))
    }

    @Test
    fun `setting the same kind again removes it`() {
        for (kind in listOf(
            h1,
            BlockKind.Quote,
            BlockKind.Bullet,
            BlockKind.Numbered,
            BlockKind.Checklist
        )) {
            val on = block("te|xt", kind)
            assertEquals("te|xt", block(on, kind), kind.toString())
        }
    }

    @Test
    fun `replaces another marker instead of stacking`() {
        assertEquals("## He|llo", block("# He|llo", h2))
        assertEquals("- He|llo", block("1. He|llo", BlockKind.Bullet))
        assertEquals("1. He|llo", block("- [x] He|llo", BlockKind.Numbered))
        assertEquals("# He|llo", block("> He|llo", h1))
        assertEquals("- [ ] He|llo", block("- He|llo", BlockKind.Checklist))
        assertEquals("- He|llo", block("- [x] He|llo", BlockKind.Bullet))
    }

    @Test
    fun `a ticked checklist line is still a checklist when toggled off`() {
        assertEquals("He|llo", block("- [x] He|llo", BlockKind.Checklist))
    }

    @Test
    fun `headings of any depth are recognized`() {
        assertEquals("# T|", block("#### T|", h1))
        assertEquals("T|", block("## T|", h2))
    }

    @Test
    fun `indentation is kept`() {
        assertEquals("  - it|em", block("  it|em", BlockKind.Bullet))
        assertEquals("  1. it|em", block("  - it|em", BlockKind.Numbered))
        assertEquals("  it|em", block("  - it|em", BlockKind.Bullet))
    }

    @Test
    fun `nested quotes are removed together`() {
        assertEquals("te|xt", block("> > te|xt", BlockKind.Quote))
    }

    @Test
    fun `an empty line gets the marker`() {
        assertEquals("a\n- |\nb", block("a\n|\nb", BlockKind.Bullet))
        assertEquals("- |", block("|", BlockKind.Bullet))
    }

    @Test
    fun `a bare marker line counts as that block`() {
        assertEquals("|", block("-|", BlockKind.Bullet))
        assertEquals(BlockKind.Heading(1), blockAt("#", TextRange(0, 0)))
        assertEquals(BlockKind.Bullet, blockAt("-", TextRange(0, 0)))
    }

    @Test
    fun `applies to every line of the selection and numbers them`() {
        assertEquals("- ‹a\n- b\n- c›", block("‹a\nb\nc›", BlockKind.Bullet))
        assertEquals("1. ‹a\n2. b\n3. c›", block("‹a\nb\nc›", BlockKind.Numbered))
    }

    @Test
    fun `blank lines inside a selection are skipped and do not break numbering`() {
        assertEquals("1. ‹a\n\n2. b›", block("‹a\n\nb›", BlockKind.Numbered))
    }

    @Test
    fun `a mixed selection sets the kind on all lines`() {
        assertEquals("‹- a\n- b›", block("‹- a\nb›", BlockKind.Bullet))
    }

    @Test
    fun `a full selection removes the kind from all lines`() {
        assertEquals("‹a\nb›", block("‹- a\n- b›", BlockKind.Bullet))
    }

    @Test
    fun `a selection ending at a line start excludes that line`() {
        assertEquals("- ‹a\n›b", block("‹a\n›b", BlockKind.Bullet))
    }

    @Test
    fun `only the touched lines change and CRLF is kept`() {
        val m = marked("x\r\n‹a\r\nb›\r\ny")
        val result = setBlock(m.text, m.selection, BlockKind.Quote)
        assertEquals("x\r\n> a\r\n> b\r\ny", result.text)
    }

    @Test
    fun `the selection start inside a removed marker snaps to the line start`() {
        val result = setBlock("- a", TextRange(1, 1), BlockKind.Bullet)
        assertEquals(EditResult("a", TextRange(0, 0)), result)
    }

    @Test
    fun `an offset inside a replaced marker lands inside the new one`() {
        // Caret between the two # of "## a" when switching to a one-character-longer marker.
        val result = setBlock("## a", TextRange(1, 1), BlockKind.Heading(3))
        assertEquals(EditResult("### a", TextRange(1, 1)), result)
    }

    @Test
    fun `the block of a line is found from the selection start`() {
        assertNull(blockAt("plain", TextRange(0, 0)))
        assertEquals(BlockKind.Quote, blockAt("> q", TextRange(2, 2)))
        assertEquals(BlockKind.Numbered, blockAt("12) x", TextRange(0, 0)))
        assertEquals(BlockKind.Checklist, blockAt("a\n* [X] x", TextRange(3, 3)))
        assertEquals(BlockKind.Heading(2), blockAt("## h", TextRange(0, 0)))
    }

    @Test
    fun `text that only looks like a marker is plain`() {
        assertNull(blockAt("**bold** word", TextRange(0, 0)))
        assertNull(blockAt("-5 degrees", TextRange(0, 0)))
        assertNull(blockAt("3.14 is pi", TextRange(0, 0)))
        assertNull(blockAt("#hashtag", TextRange(0, 0)))
    }

    @Test
    fun `invalid heading levels and selections are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { BlockKind.Heading(0) }
        assertThrows(IllegalArgumentException::class.java) { BlockKind.Heading(7) }
        assertThrows(IllegalArgumentException::class.java) { setBlock("a", TextRange(0, 2), h1) }
        assertThrows(IllegalArgumentException::class.java) { blockAt("a", TextRange(2, 2)) }
    }

    @Test
    fun `a blank line gets the marker even when a range ends right after it`() {
        val result = setBlock("a\n\n\nb", TextRange(2, 3), BlockKind.Bullet)
        assertEquals("a\n- \n\nb", result.text)
    }

    @Test
    fun `tab indentation of a plain line is kept`() {
        assertEquals("\t- a|", block("\ta|", BlockKind.Bullet))
    }

    @Test
    fun `a range of only blank lines changes nothing`() {
        val result = setBlock("\n\n\n", TextRange(0, 2), BlockKind.Bullet)
        assertEquals(EditResult("\n\n\n", TextRange(0, 2)), result)
    }
}
