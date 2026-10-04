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
            "**bold** start|**bold** start",
            "\t# tab indent|tab indent"
        ]
    )
    fun `title strips heading and list syntax`(line: String, expected: String) {
        assertEquals(expected, NoteSummary.title(line))
    }

    @Test
    fun `title is the first line with content`() {
        assertEquals("Second", NoteSummary.title("\n  \r\n#\n- \n- [ ]\n1.\nSecond\nThird"))
        assertEquals("Title", NoteSummary.title("\r\nTitle\r\nbody"))
    }

    @Test
    fun `title of empty or syntax only text is empty`() {
        assertEquals("", NoteSummary.title(""))
        assertEquals("", NoteSummary.title(" \n\t\n"))
        assertEquals("", NoteSummary.title("#\n-\n- [ ]"))
    }

    @Test
    fun `preview is the content after the title joined with spaces`() {
        val text = "# Title\n\nFirst line\r\n- [ ] task\n\n> quote stays\n3. last"
        assertEquals("First line task > quote stays last", NoteSummary.preview(text))
    }

    @Test
    fun `preview is empty when the note has at most a title`() {
        assertEquals("", NoteSummary.preview(""))
        assertEquals("", NoteSummary.preview("Only a title"))
        assertEquals("", NoteSummary.preview("# Title\n\n  \n-\n"))
    }

    @Test
    fun `preview is cut at the maximum length`() {
        assertEquals("abcde", NoteSummary.preview("t\nabcdef", maxLength = 5))
        assertEquals("abcdef", NoteSummary.preview("t\nabcdef", maxLength = 6))
        assertEquals("", NoteSummary.preview("t\nabcdef", maxLength = 0))
        assertEquals(120, NoteSummary.preview("t\n" + "x".repeat(500)).length)
    }

    @Test
    fun `preview never splits a surrogate pair`() {
        val text = "t\nab😀cd"
        assertEquals("ab", NoteSummary.preview(text, maxLength = 3))
        assertEquals("ab😀", NoteSummary.preview(text, maxLength = 4))
    }

    @Test
    fun `negative preview length is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            NoteSummary.preview("a\nb", maxLength = -1)
        }
    }

    @Test
    fun `corpus titles and previews`() {
        assertEquals("Welcome to Nextcloud Notes", NoteSummary.title(Corpus.load("welcome")))
        assertEquals("Shopping", NoteSummary.title(Corpus.load("shopping-crlf")))
        assertEquals(
            "Milk Eggs Free range Bread Coffee **dark** Don't forget the *bag*.",
            NoteSummary.preview(Corpus.load("shopping-crlf"))
        )
        assertEquals("Viaje a Japón 🇯🇵", NoteSummary.title(Corpus.load("unicode")))
        assertEquals("Project plan", NoteSummary.title(Corpus.load("nested")))
        assertEquals("Snippets", NoteSummary.title(Corpus.load("code-fences")))
        assertEquals("Budget", NoteSummary.title(Corpus.load("table")))
        assertEquals("#hashtag not a heading", NoteSummary.title(Corpus.load("tricky")))
        assertEquals("", NoteSummary.title(Corpus.load("empty")))
        assertEquals("", NoteSummary.title(Corpus.load("blank")))
    }
}
