// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

import com.qtekfun.ultimatenotes.domain.TextRange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class StyleRunsTest {
    /** The styled slices of [text] as role to source text, in application order. */
    private fun runs(text: String) = StyleRuns.of(text, MarkdownAnalyzer.analyze(text))
        .map { it.role to text.substring(it.range.start, it.range.end) }

    @Test
    fun `plain text has no runs`() {
        assertEquals(emptyList<Pair<StyleRole, String>>(), runs("just words"))
    }

    @Test
    fun `heading is styled and its hashes are quiet`() {
        assertEquals(
            listOf(StyleRole.Heading(2) to "## Title", StyleRole.Marker to "##"),
            runs("## Title")
        )
    }

    @Test
    fun `setext heading has no marker run`() {
        assertEquals(listOf(StyleRole.Heading(1) to "Title\n====="), runs("Title\n====="))
    }

    @Test
    fun `inline styles mark their delimiters`() {
        assertEquals(
            listOf(StyleRole.Bold to "**b**", StyleRole.Marker to "**", StyleRole.Marker to "**"),
            runs("**b**")
        )
        assertEquals(
            listOf(StyleRole.Italic to "*i*", StyleRole.Marker to "*", StyleRole.Marker to "*"),
            runs("*i*")
        )
        assertEquals(
            listOf(StyleRole.Strike to "~~s~~", StyleRole.Marker to "~~", StyleRole.Marker to "~~"),
            runs("~~s~~")
        )
        assertEquals(
            listOf(StyleRole.Code to "``c``", StyleRole.Marker to "``", StyleRole.Marker to "``"),
            runs("``c``")
        )
    }

    @Test
    fun `nested styles each get their runs`() {
        val roles = runs("***x***").map { it.first }
        assertTrue(StyleRole.Bold in roles && StyleRole.Italic in roles)
    }

    @Test
    fun `link marks brackets and target`() {
        assertEquals(
            listOf(
                StyleRole.Link to "[a](http://x)",
                StyleRole.Marker to "[",
                StyleRole.Marker to "](http://x)"
            ),
            runs("[a](http://x)")
        )
    }

    @Test
    fun `autolink is one run`() {
        assertEquals(listOf(StyleRole.Link to "<http://x.org>"), runs("<http://x.org>"))
    }

    @Test
    fun `quote styles every line and dims its markers`() {
        assertEquals(
            listOf(
                StyleRole.Quote to "> a",
                StyleRole.Marker to "> ",
                StyleRole.Quote to "> b",
                StyleRole.Marker to "> "
            ),
            runs("> a\n> b")
        )
    }

    @Test
    fun `list markers are quiet`() {
        assertEquals(listOf(StyleRole.Marker to "-", StyleRole.Marker to "-"), runs("- a\n- b"))
        assertEquals(listOf(StyleRole.Marker to "12."), runs("12. a"))
    }

    @Test
    fun `checklist has a marker a box and strikes ticked text`() {
        assertEquals(
            listOf(
                StyleRole.Marker to "-",
                StyleRole.Checkbox(false) to "[ ]"
            ),
            runs("- [ ] a")
        )
        assertEquals(
            listOf(
                StyleRole.Done to " b",
                StyleRole.Marker to "-",
                StyleRole.Checkbox(true) to "[x]"
            ),
            runs("- [x] b\n- plain").take(3)
        )
    }

    @Test
    fun `opaque syntax gets no runs`() {
        assertEquals(emptyList<Pair<StyleRole, String>>(), runs("```\n**not bold**\n```"))
        assertEquals(emptyList<Pair<StyleRole, String>>(), runs("| a | b |\n|---|---|\n| 1 | 2 |"))
    }

    @Test
    fun `runs are clamped to a shorter text`() {
        val segments = MarkdownAnalyzer.analyze("**bold** and more")
        val shorter = "**bo"
        val result = StyleRuns.of(shorter, segments)
        assertTrue(result.all { it.range.end <= shorter.length })
        assertTrue(StyleRuns.of("", segments).isEmpty())
    }

    @ParameterizedTest
    @MethodSource("corpus")
    fun `every corpus note yields valid ordered runs`(name: String) {
        val text = Corpus.load(name)
        for (run in StyleRuns.of(text, MarkdownAnalyzer.analyze(text))) {
            assertTrue(
                run.range.start in 0 until run.range.end && run.range.end <= text.length,
                name
            )
        }
    }

    @Test
    fun `checkbox box range is the one the checklist parser toggles`() {
        val text = "- [ ] a"
        val box = StyleRuns.of(text, MarkdownAnalyzer.analyze(text))
            .single { it.role is StyleRole.Checkbox }.range
        assertEquals(TextRange(2, 5), box)
    }

    companion object {
        @JvmStatic
        fun corpus() = Corpus.names
    }
}
