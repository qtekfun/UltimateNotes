// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class PlainTextTest {
    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(
        delimiter = '→',
        value = [
            "**bold** text→bold text",
            "__bold__ text→bold text",
            "*italic* and _italic_→italic and italic",
            "~~gone~~ stays→gone stays",
            "***both***→both",
            "`code` here→code here",
            "``a `tick` b``→a `tick` b",
            "[a link](https://example.org/x) now→a link now",
            "![a picture](img.png) after→a picture after",
            "[ref link][1] end→ref link end",
            "<https://example.org> open→https://example.org open",
            "\\*not italic\\* here→*not italic* here",
            "snake_case_name stays→snake_case_name stays",
            "2 * 3 * 4→2 * 3 * 4",
            "dangling ** marker→dangling marker",
            "| a | b |c→a b c",
            "plain text→plain text"
        ]
    )
    fun `inline syntax is removed and the text kept`(source: String, expected: String) {
        assertEquals(expected, PlainText.line(source).replace(Regex("\\s+"), " "))
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(
        delimiter = '→',
        value = [
            "# Heading→Heading",
            "###### Deep→Deep",
            "- item→item",
            "* item→item",
            "+ item→item",
            "1. first→first",
            "2) second→second",
            "- [ ] todo→todo",
            "- [x] done→done",
            "   - [X] nested→nested",
            "> quoted→quoted",
            ">> twice→twice",
            "#hashtag stays→#hashtag stays",
            "-minus stays→-minus stays"
        ]
    )
    fun `block syntax at the start of a line is removed`(source: String, expected: String) {
        assertEquals(expected, PlainText.line(source))
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @ValueSource(
        strings = [
            "---", "***", "___", "- - -", "```", "```kotlin", "~~~",
            "|---|---|", "| :--- | ---: |", "   ", ""
        ]
    )
    fun `lines without any text are empty`(source: String) {
        assertEquals("", PlainText.line(source))
    }

    @Test
    fun `lines keeps only the lines that have text, in order`() {
        val text = "# Title\n\n---\n**Day 1** - arrive\r\n```\n- [x] bag\n"
        assertEquals(listOf("Title", "Day 1 - arrive", "bag"), PlainText.lines(text).toList())
    }

    @Test
    fun `an empty note has no lines`() {
        assertEquals(emptyList<String>(), PlainText.lines("").toList())
    }

    @Test
    fun `unicode and emoji pass through untouched`() {
        assertEquals("Café ☕ 日本語 😀", PlainText.line("**Café** ☕ _日本語_ 😀"))
    }

    @Test
    fun `private-use characters outside the escape range are kept as they are`() {
        assertEquals("a\uE000b\uE200c", PlainText.line("**a\uE000b\uE200c**"))
    }

    @Test
    fun `an escaped character is never read as syntax`() {
        assertEquals("a*b* \\c", PlainText.line("a\\*b\\* \\\\c"))
    }
}
