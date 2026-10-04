// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class NoteSummaryTest {
    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "Plain title|Plain title",
            "# Heading|Heading",
            "###### Deep heading|Deep heading",
            "####### seven hashes|####### seven hashes",
            "#hashtag|#hashtag",
            "- bullet|bullet",
            "* star|star",
            "+ plus|plus",
            "- [ ] open task|open task",
            "- [x] done task|done task",
            "  * [X] indented task  |indented task",
            "12. numbered|numbered",
            "3) paren|paren",
            "1234567890. too many digits|1234567890. too many digits",
            "-no space|-no space",
            "- [x]glued|[x]glued",
            "**bold** start|bold start",
            "\t# tab indent|tab indent"
        ]
    )
    fun `first line strips heading and list syntax`(line: String, expected: String) {
        assertEquals(expected, NoteSummary.firstLine(line))
    }

    @Test
    fun `first line is the first line with content`() {
        assertEquals("Second", NoteSummary.firstLine("\n  \r\n#\n- \n- [ ]\n1.\nSecond\nThird"))
        assertEquals("Title", NoteSummary.firstLine("\r\nTitle\r\nbody"))
    }

    @Test
    fun `first line of empty or syntax only text is empty`() {
        assertEquals("", NoteSummary.firstLine(""))
        assertEquals("", NoteSummary.firstLine(" \n\t\n"))
        assertEquals("", NoteSummary.firstLine("#\n-\n- [ ]"))
    }

    @Test
    fun `initial title is the first line cut to the server's limit`() {
        assertEquals("Plan", NoteSummary.initialTitle("# Plan\nbody"))
        assertEquals("", NoteSummary.initialTitle("#\n-\n"))
        assertEquals("x".repeat(100), NoteSummary.initialTitle("x".repeat(250)))
    }

    @Test
    fun `initial title never ends in half a surrogate pair`() {
        val text = "x".repeat(99) + "😀 tail"
        assertEquals("x".repeat(99), NoteSummary.initialTitle(text))
    }

    @Test
    fun `preview drops the first line when it is the title`() {
        val text = "# Title\n\nFirst line\r\n- [ ] task\n\n> quote stays\n3. last"
        assertEquals(
            "First line task quote stays last",
            NoteSummary.preview(text, title = "Title")
        )
    }

    @Test
    fun `preview keeps the whole body when the first line is not the title`() {
        assertEquals(
            "Dear diary today was long",
            NoteSummary.preview("Dear diary\ntoday was long", title = "Journal")
        )
        assertEquals("Only a body", NoteSummary.preview("Only a body", title = ""))
        assertEquals("Only a body", NoteSummary.preview("Only a body", title = "  "))
    }

    @Test
    fun `preview recognises a title the server sanitized or cut`() {
        // The server strips : ? and the like from titles, so the stored title differs from the line.
        assertEquals("body", NoteSummary.preview("What: now?\nbody", title = "What now"))
        val long = "y".repeat(130)
        assertEquals("rest", NoteSummary.preview("$long\nrest", title = "y".repeat(100)))
        // A short title that merely starts the line does not hide it.
        assertEquals("Plan trip rest", NoteSummary.preview("Plan trip\nrest", title = "Plan"))
    }

    @Test
    fun `preview is empty when the note has at most a title`() {
        assertEquals("", NoteSummary.preview("", title = "T"))
        assertEquals("", NoteSummary.preview("Only a title", title = "Only a title"))
        assertEquals("", NoteSummary.preview("# Title\n\n  \n-\n", title = "Title"))
    }

    @Test
    fun `preview is cut at the maximum length`() {
        assertEquals("abcde", NoteSummary.preview("t\nabcdef", "t", maxLength = 5))
        assertEquals("abcdef", NoteSummary.preview("t\nabcdef", "t", maxLength = 6))
        assertEquals("", NoteSummary.preview("t\nabcdef", "t", maxLength = 0))
        assertEquals(120, NoteSummary.preview("t\n" + "x".repeat(500), "t").length)
    }

    @Test
    fun `preview never splits a surrogate pair`() {
        val text = "t\nab😀cd"
        assertEquals("ab", NoteSummary.preview(text, "t", maxLength = 3))
        assertEquals("ab😀", NoteSummary.preview(text, "t", maxLength = 4))
    }

    @Test
    fun `negative preview length is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            NoteSummary.preview("a\nb", "a", maxLength = -1)
        }
    }

    @Test
    fun `corpus titles and previews`() {
        assertEquals("Welcome to Nextcloud Notes", NoteSummary.firstLine(Corpus.load("welcome")))
        assertEquals("Shopping", NoteSummary.firstLine(Corpus.load("shopping-crlf")))
        assertEquals(
            "Milk Eggs Free range Bread Coffee dark Don't forget the bag.",
            NoteSummary.preview(Corpus.load("shopping-crlf"), title = "Shopping")
        )
        assertEquals("Viaje a Japón 🇯🇵", NoteSummary.firstLine(Corpus.load("unicode")))
        assertEquals("Project plan", NoteSummary.firstLine(Corpus.load("nested")))
        assertEquals("Snippets", NoteSummary.firstLine(Corpus.load("code-fences")))
        assertEquals("Budget", NoteSummary.firstLine(Corpus.load("table")))
        assertEquals("#hashtag not a heading", NoteSummary.firstLine(Corpus.load("tricky")))
        assertEquals("", NoteSummary.firstLine(Corpus.load("empty")))
        assertEquals("", NoteSummary.firstLine(Corpus.load("blank")))
    }

    @Test
    fun `a very long note previews only its first lines`() {
        val body = (1..100_000).joinToString("\n") {
            "**line $it** with _some_ [link](https://x.org)"
        }
        assertEquals(
            "line 1 with some link line 2 with some link line 3 with some link",
            NoteSummary.preview("Title\n$body", title = "Title", maxLength = 65)
        )
    }

    @Test
    fun `a preview stops at the first line that fills it`() {
        assertEquals(
            "abcde",
            NoteSummary.preview("abcdefghij\nnext line", title = "other", maxLength = 5)
        )
    }
}
