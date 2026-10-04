// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NoteLinksTest {
    private fun caret(text: String, offset: Int) = NoteLinks.openableAtCaret(text, offset, offset)

    private fun hit(text: String, offset: Int, endInclusive: Boolean) =
        NoteLinks.linkAt(MarkdownAnalyzer.analyze(text), offset, endInclusive)

    @Test
    fun `caret inside the link text or the url finds the link`() {
        val text = "see [docs](https://example.org/a) now"
        val start = text.indexOf('[')
        val end = text.indexOf(')') + 1
        for (offset in start + 1 until end) {
            assertEquals("https://example.org/a", caret(text, offset), "offset $offset")
        }
    }

    @Test
    fun `the start boundary is inside and the position before it is not`() {
        val text = "see [docs](https://example.org) now"
        val start = text.indexOf('[')
        assertNull(caret(text, start - 1))
        assertEquals("https://example.org", caret(text, start))
    }

    @Test
    fun `the end boundary is inside for a caret but not for a character`() {
        val text = "see [docs](https://example.org) now"
        val end = text.indexOf(')') + 1
        assertEquals("https://example.org", caret(text, end))
        assertNull(caret(text, end + 1))
        assertEquals("https://example.org", NoteLinks.openableAtChar(text, end - 1))
        assertNull(NoteLinks.openableAtChar(text, end))
    }

    @Test
    fun `a selection that is not collapsed finds nothing`() {
        val text = "[docs](https://example.org)"
        assertNull(NoteLinks.openableAtCaret(text, 1, 3))
    }

    @Test
    fun `text without links finds nothing`() {
        assertNull(caret("", 0))
        assertNull(caret("just words", 3))
    }

    @Test
    fun `emphasis inside the link text still belongs to the link`() {
        val text = "[a **bold** and *it* `c`](https://example.org)"
        val bold = text.indexOf("bold")
        val italic = text.indexOf("it")
        assertEquals("https://example.org", caret(text, bold))
        assertEquals("https://example.org", caret(text, italic))
        assertEquals("https://example.org", NoteLinks.openableAtChar(text, bold))
    }

    @Test
    fun `a link inside emphasis, a quote, a list and a heading is found`() {
        val link = "[x](https://example.org)"
        for (text in listOf("**$link**", "> $link", "- $link", "# $link", "- [ ] $link")) {
            assertEquals("https://example.org", caret(text, text.indexOf("[x]") + 1), text)
        }
    }

    @Test
    fun `adjacent links choose the one starting at the caret`() {
        val text = "[a](https://one.example)[b](https://two.example)"
        val boundary = text.indexOf(")[") + 1
        assertEquals("https://two.example", caret(text, boundary))
        assertEquals("https://one.example", caret(text, boundary - 1))
        assertEquals("https://two.example", NoteLinks.openableAtChar(text, boundary))
        assertEquals("https://one.example", NoteLinks.openableAtChar(text, boundary - 1))
        assertEquals("https://two.example", caret(text, text.length))
    }

    @Test
    fun `links on separate lines are told apart`() {
        val text = "[a](https://one.example)\n\n[b](https://two.example)"
        assertEquals("https://one.example", caret(text, 2))
        assertNull(caret(text, text.indexOf('\n') + 1))
        assertEquals("https://two.example", caret(text, text.length - 3))
    }

    @Test
    fun `CRLF line endings do not shift the offsets`() {
        val text = "first line\r\nsecond [a](https://example.org) end\r\n[b](tel:+34600)"
        assertEquals("https://example.org", caret(text, text.indexOf("[a]") + 1))
        assertNull(caret(text, text.indexOf("first")))
        assertEquals("tel:+34600", caret(text, text.indexOf("[b]") + 1))
        assertNull(caret(text, text.indexOf("end")))
    }

    @Test
    fun `an emoji before the link counts as two offsets`() {
        val text = "😀😀 [a](https://example.org)"
        val start = text.indexOf('[')
        assertEquals(5, start)
        assertNull(caret(text, start - 1))
        assertEquals("https://example.org", caret(text, start + 1))
        assertEquals("https://example.org", NoteLinks.openableAtChar(text, start))
    }

    @Test
    fun `autolinks in angle brackets are links`() {
        val text = "mail <https://example.org/x> or <me@example.org>"
        assertEquals("https://example.org/x", caret(text, text.indexOf("example") + 1))
        assertEquals("mailto:me@example.org", caret(text, text.indexOf("me@") + 1))
    }

    @Test
    fun `reference links with a definition are links`() {
        val text = "see [docs][d] now\n\n[d]: https://example.org"
        assertEquals("https://example.org", caret(text, text.indexOf("[docs]") + 1))
    }

    @Test
    fun `bare urls and images are not links`() {
        val bare = "go to https://example.org now"
        assertNull(caret(bare, bare.indexOf("example")))
        val image = "![alt](https://example.org/i.png)"
        assertNull(caret(image, 3))
        val code = "`[a](https://example.org)`"
        assertNull(caret(code, 3))
    }

    @Test
    fun `a link whose destination is refused is found but not openable`() {
        val text = "[x](javascript:alert(1)) [y](notes/other.md) [z](#top)"
        assertEquals("javascript:alert(1)", hit(text, 1, true)?.destination)
        assertNull(caret(text, 1))
        assertNull(caret(text, text.indexOf("[y]") + 1))
        assertNull(caret(text, text.indexOf("[z]") + 1))
    }

    @Test
    fun `a destination with parentheses and a title is read as the analyzer reads it`() {
        val text = "[w](https://example.org/Foo_(bar) \"title\")"
        assertEquals("https://example.org/Foo_(bar)", caret(text, 2))
    }

    @Test
    fun `an angle-bracketed destination with spaces is refused`() {
        assertNull(caret("[w](<https://example.org/a b>)", 2))
    }

    @Test
    fun `a hit reports its range and never prints the destination`() {
        val text = "x [a](https://secret.example/token)"
        val found = hit(text, 3, true)!!
        assertEquals(2, found.range.start)
        assertEquals(text.length, found.range.end)
        assertEquals("https://secret.example/token", found.destination)
        assertFalse(found.toString().contains("secret"))
    }
}
