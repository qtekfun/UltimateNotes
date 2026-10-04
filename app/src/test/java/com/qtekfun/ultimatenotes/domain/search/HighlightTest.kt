// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.search

import com.qtekfun.ultimatenotes.data.local.model.SearchMarkers
import com.qtekfun.ultimatenotes.domain.TextRange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HighlightTest {
    private fun matches(text: String, query: String) =
        Highlight.matches(text, SearchQuery.parse(query)!!)

    private fun slices(text: String, query: String) =
        matches(text, query).map { text.substring(it.start, it.end) }

    private fun marked(vararg parts: String) = parts.mapIndexed { i, part ->
        if (i % 2 == 1) SearchMarkers.OPEN + part + SearchMarkers.CLOSE else part
    }.joinToString("")

    // --- matches ----------------------------------------------------------------------------------

    @Test
    fun `whole words match, ignoring case`() {
        assertEquals(listOf("Milk", "MILK"), slices("Milk and eggs, MILK again", "milk"))
    }

    @Test
    fun `the last term matches as a prefix and only the typed part is highlighted`() {
        assertEquals(listOf("choc"), slices("A chocolate cake", "choc"))
    }

    @Test
    fun `earlier terms must be whole words`() {
        assertEquals(listOf("cake"), slices("chocolate cake", "choc cake"))
        assertEquals(listOf("cake", "ca"), slices("cake carrot", "cake ca"))
        // "cake" is not prefix-matched by the first term of "ca cake".
        assertEquals(listOf("cake"), slices("cake", "ca cake"))
    }

    @Test
    fun `a word that matches several terms is highlighted once, as long as the longest`() {
        assertEquals(listOf("cake"), slices("cake", "cake ca"))
    }

    @Test
    fun `accents are ignored on both sides`() {
        assertEquals(listOf("Café"), slices("Un Café con leche", "cafe"))
        assertEquals(listOf("cafe"), slices("un cafe", "CAFÉ"))
        assertEquals(listOf("AÑO"), slices("AÑO nuevo", "ano"))
    }

    @Test
    fun `a decomposed accent stays inside the highlight`() {
        val text = "café solo"

        assertEquals(listOf(TextRange(0, 5)), matches(text, "cafe"))
        assertEquals(listOf(TextRange(0, 3)), matches(text, "caf"))
    }

    @Test
    fun `emoji and surrogate pairs keep offsets right`() {
        val text = "😀 hola 😀😀 mundo"

        assertEquals(listOf(TextRange(3, 7), TextRange(13, 18)), matches(text, "hola mundo"))
        assertEquals(listOf("𝒜b"), slices("x 𝒜b y", "𝒜b"))
    }

    @Test
    fun `a prefix never splits a surrogate pair`() {
        val text = "𝒜bc"

        assertEquals(listOf(TextRange(0, 2)), matches(text, "𝒜"))
    }

    @Test
    fun `line breaks and punctuation separate words`() {
        assertEquals(listOf("milk", "eggs"), slices("buy milk\r\neggs.", "milk eggs"))
        assertEquals(listOf("foo", "bar"), slices("foo-bar", "foo bar"))
    }

    @Test
    fun `nothing matches nothing`() {
        assertEquals(emptyList<TextRange>(), matches("", "a"))
        assertEquals(emptyList<TextRange>(), matches("zzz", "a"))
    }

    // --- snippet ----------------------------------------------------------------------------------

    @Test
    fun `markers are removed and their text becomes ranges`() {
        val snippet = Highlight.snippet(marked("…buy ", "milk", " and ", "eggs", " today…"))

        assertEquals("…buy milk and eggs today…", snippet.text)
        assertEquals(listOf(TextRange(5, 9), TextRange(14, 18)), snippet.ranges)
    }

    @Test
    fun `crlf and runs of whitespace collapse to one space and keep ranges right`() {
        val snippet = Highlight.snippet(
            marked("  first\r\n\r\n  line\n", "needle", "\t\tnext  \r\n")
        )

        assertEquals("first line needle next", snippet.text)
        assertEquals(listOf(TextRange(11, 17)), snippet.ranges)
    }

    @Test
    fun `a match that starts with whitespace does not include it`() {
        val snippet = Highlight.snippet(marked("a", "\r\nword", " b"))

        assertEquals("a word b", snippet.text)
        assertEquals(listOf(TextRange(2, 6)), snippet.ranges)
    }

    @Test
    fun `emoji and accents are kept as they are`() {
        val snippet = Highlight.snippet(marked("😀 ", "Café", " 😀😀"))

        assertEquals("😀 Café 😀😀", snippet.text)
        assertEquals(listOf(TextRange(3, 7)), snippet.ranges)
    }

    @Test
    fun `an unclosed match runs to the end and trailing space is trimmed`() {
        val snippet = Highlight.snippet("ab ${SearchMarkers.OPEN}cd  \r\n")

        assertEquals("ab cd", snippet.text)
        assertEquals(listOf(TextRange(3, 5)), snippet.ranges)
    }

    @Test
    fun `stray or empty markers produce no ranges`() {
        assertEquals(
            Highlighted("ab", emptyList()),
            Highlight.snippet("a${SearchMarkers.CLOSE}b${SearchMarkers.OPEN}${SearchMarkers.CLOSE}")
        )
        assertEquals(Highlighted.EMPTY, Highlight.snippet(""))
        assertEquals(Highlighted.EMPTY, Highlight.snippet(" \r\n "))
    }

    @Test
    fun `a match that is only whitespace disappears`() {
        val snippet = Highlight.snippet("a${SearchMarkers.OPEN}  ${SearchMarkers.CLOSE}b")

        assertEquals("a b", snippet.text)
        assertEquals(emptyList<TextRange>(), snippet.ranges)
    }

    @Test
    fun `a match cut off by trimming is clamped`() {
        val snippet = Highlight.snippet("x${SearchMarkers.OPEN}y ${SearchMarkers.CLOSE}")

        assertEquals("xy", snippet.text)
        assertEquals(listOf(TextRange(1, 2)), snippet.ranges)
    }
}
