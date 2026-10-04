// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

import com.qtekfun.ultimatenotes.domain.TextRange
import com.qtekfun.ultimatenotes.domain.checklist.ChecklistParser
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class MarkdownAnalyzerTest {
    /** The source slice and kind of each top-level segment. */
    private fun top(text: String) = MarkdownAnalyzer.analyze(text).map {
        it.kind to
            text.substring(it.range.start, it.range.end)
    }

    private fun slices(text: String, segments: List<Segment>) =
        segments.map { text.substring(it.range.start, it.range.end) }

    @Test
    fun `empty and blank text have no segments`() {
        assertEquals(emptyList<Segment>(), MarkdownAnalyzer.analyze(""))
        assertEquals(emptyList<Segment>(), MarkdownAnalyzer.analyze(" \n\n\t\n"))
        assertEquals(emptyList<Segment>(), MarkdownAnalyzer.analyze("plain words only"))
    }

    @Test
    fun `headings report their level for ATX and setext styles`() {
        assertEquals(
            listOf(
                SegmentKind.Heading(1) to "# One",
                SegmentKind.Heading(2) to "## Two",
                SegmentKind.Heading(3) to "### Three",
                SegmentKind.Heading(2) to "Setext\n------"
            ),
            top("# One\n\n## Two\n\n### Three\n\nSetext\n------")
        )
    }

    @Test
    fun `inline styles are found in both delimiter spellings and nest`() {
        val text = "**a** __b__ *c* _d_ ~~e~~ ~f~ `g` ***h***"
        val kinds = MarkdownAnalyzer.analyze(text).map { it.kind }
        assertEquals(
            listOf(
                SegmentKind.Bold,
                SegmentKind.Bold,
                SegmentKind.Italic,
                SegmentKind.Italic,
                SegmentKind.Strike,
                SegmentKind.Strike,
                SegmentKind.Code,
                SegmentKind.Italic
            ),
            kinds
        )
        val nested = MarkdownAnalyzer.analyze("a **bold _both_** z").single()
        assertEquals(SegmentKind.Bold, nested.kind)
        assertEquals(SegmentKind.Italic, nested.children.single().kind)
        assertEquals(
            "_both_",
            "a **bold _both_** z".substring(
                nested.children.single().range.start,
                nested.children.single().range.end
            )
        )
    }

    @Test
    fun `links carry their destination and cover the whole syntax`() {
        val text = "[x](https://a.b \"t\") <https://c.d> [ref][1]\n\n[1]: https://e.f"
        val segments = MarkdownAnalyzer.analyze(text)
        assertEquals(
            listOf(
                SegmentKind.Link("https://a.b"),
                SegmentKind.Link("https://c.d"),
                SegmentKind.Link("https://e.f"),
                SegmentKind.Opaque
            ),
            segments.map { it.kind }
        )
        assertEquals(
            listOf("[x](https://a.b \"t\")", "<https://c.d>", "[ref][1]", "[1]: https://e.f"),
            slices(text, segments)
        )
    }

    @Test
    fun `bullet and numbered items keep nesting and checklists are told apart`() {
        val text = "- a\n  1. b\n  2. c\n- [x] d\n  - [ ] e"
        val list = MarkdownAnalyzer.analyze(text)
        assertEquals(
            listOf(SegmentKind.ListItem(false), SegmentKind.Checklist(true, TextRange(20, 23))),
            list.map {
                it.kind
            }
        )
        assertEquals(
            listOf(SegmentKind.ListItem(true), SegmentKind.ListItem(true)),
            list[0].children.map {
                it.kind
            }
        )
        assertEquals(
            listOf(SegmentKind.Checklist(false, TextRange(30, 33))),
            list[1].children.map {
                it.kind
            }
        )
    }

    @Test
    fun `quotes contain their inline and block content`() {
        val text = "> quoted *em*\n> - item"
        val quote = MarkdownAnalyzer.analyze(text).single()
        assertEquals(SegmentKind.Quote, quote.kind)
        assertEquals(
            listOf(SegmentKind.Italic, SegmentKind.ListItem(false)),
            quote.children.map {
                it.kind
            }
        )
    }

    @Test
    fun `unsupported syntax becomes opaque ranges without children`() {
        val text = listOf(
            "```\n**x**\n```",
            "    indented",
            "<div>*x*</div>",
            "![alt *x*](i.png)",
            "---",
            "| a | b |\n|---|---|\n| *c* | d |",
            "text <b>html</b>"
        ).joinToString("\n\n")
        val segments = MarkdownAnalyzer.analyze(text)
        val opaque = segments.filter { it.kind == SegmentKind.Opaque }
        assertEquals(
            listOf(
                "```\n**x**\n```",
                "    indented",
                "<div>*x*</div>",
                "![alt *x*](i.png)",
                "---",
                "| a | b |\n|---|---|\n| *c* | d |",
                "<b>",
                "</b>"
            ),
            slices(text, opaque)
        )
        assertTrue(opaque.all { it.children.isEmpty() })
        assertEquals(opaque.size, segments.size)
    }

    @Test
    fun `offsets are UTF-16 code units even after emoji and with CRLF`() {
        val text = "😀 **b**\r\n- [x] 日本 *i*\r\n"
        val segments = MarkdownAnalyzer.analyze(text)
        assertEquals("**b**", text.substring(segments[0].range.start, segments[0].range.end))
        val item = segments[1]
        assertEquals("- [x] 日本 *i*", text.substring(item.range.start, item.range.end))
        assertEquals(
            "[x]",
            (item.kind as SegmentKind.Checklist).box.let {
                text.substring(it.start, it.end)
            }
        )
        assertEquals(
            "*i*",
            item.children.single().let {
                text.substring(it.range.start, it.range.end)
            }
        )
    }

    @ParameterizedTest
    @MethodSource("corpus")
    fun `corpus notes yield well formed segments`(name: String) {
        val text = Corpus.load(name)
        assertWellFormed(text, MarkdownAnalyzer.analyze(text), TextRange(0, text.length))
    }

    @ParameterizedTest
    @MethodSource("corpus")
    fun `checklists are what the line parser finds minus the ones inside opaque ranges`(
        name: String
    ) {
        val text = Corpus.load(name)
        val segments = MarkdownAnalyzer.analyze(text)
        val reported = checklists(segments).map { it.box }
        val byParser = ChecklistParser.parse(text).map { it.box }
        assertTrue(byParser.containsAll(reported))
        val opaque = flatten(segments).filter { it.kind == SegmentKind.Opaque }.map { it.range }
        byParser.filterNot { it in reported }.forEach { box ->
            assertTrue(
                opaque.any {
                    box.start >= it.start && box.end <= it.end
                },
                "$name: unexplained checklist at $box"
            )
        }
    }

    @ParameterizedTest
    @MethodSource("corpus")
    fun `toggling any reported checklist changes one character and leaves the structure alone`(
        name: String
    ) {
        val text = Corpus.load(name)
        val before = MarkdownAnalyzer.analyze(text)
        for (checklist in checklists(before)) {
            val toggled = ChecklistParser.toggle(text, checklist.box)
            assertEquals(text.length, toggled.length)
            assertEquals(1, text.indices.count { text[it] != toggled[it] })
            assertEquals(
                shape(before, flipAt = checklist.box),
                shape(MarkdownAnalyzer.analyze(toggled))
            )
        }
    }

    @Test
    fun `corpus checklist counts match what a reader sees`() {
        val counts = Corpus.names.associateWith {
            checklists(MarkdownAnalyzer.analyze(Corpus.load(it))).size
        }
        assertEquals(
            mapOf(
                "welcome" to 0, "shopping-crlf" to 5, "unicode" to 3, "nested" to 3,
                "code-fences" to 1,
                "table" to 0, "tricky" to 4, "empty" to 0, "blank" to 0
            ),
            counts
        )
    }

    @Test
    fun `random markdown soup always yields well formed segments and stable toggles`() {
        val random = Random(SEED)
        val pieces = listOf(
            "# ", "## ", "- ", "* ", "+ ", "1. ", "[ ] ", "[x] ", "> ", "**", "_", "*", "~~", "`",
            "```\n", "\n", "\n\n", "\r\n", "  ", "\t", "    ", "|", "|-|\n", "word ", "😀",
            "[l](u)", "[l][r]", "[r]: u\n", "<b>", "</b>", "![i](p)", "---\n"
        )
        repeat(ITERATIONS) {
            val text = List(random.nextInt(MAX_PIECES)) { pieces.random(random) }.joinToString("")
            val segments = MarkdownAnalyzer.analyze(text)
            assertWellFormed(text, segments, TextRange(0, text.length))
            for (checklist in checklists(segments)) {
                val toggled = ChecklistParser.toggle(text, checklist.box)
                assertEquals(1, text.indices.count { text[it] != toggled[it] })
                assertEquals(
                    shape(segments, flipAt = checklist.box),
                    shape(MarkdownAnalyzer.analyze(toggled))
                )
            }
        }
    }

    private fun assertWellFormed(text: String, segments: List<Segment>, parent: TextRange) {
        var previousEnd = parent.start
        for (segment in segments) {
            val range = segment.range
            assertTrue(range.start >= previousEnd, "overlapping or unordered siblings at $range")
            assertTrue(range.end <= parent.end, "child $range leaves its parent $parent")
            previousEnd = range.end
            val kind = segment.kind
            if (kind is SegmentKind.Checklist) {
                assertTrue(kind.box.start >= range.start && kind.box.end <= range.end)
                assertEquals(
                    if (kind.checked) "[x]" else "[ ]",
                    text.substring(kind.box.start, kind.box.end).lowercase()
                )
            }
            if (kind == SegmentKind.Opaque) assertTrue(segment.children.isEmpty())
            assertWellFormed(text, segment.children, range)
        }
    }

    private fun flatten(segments: List<Segment>): List<Segment> = segments.flatMap {
        listOf(it) +
            flatten(it.children)
    }

    private fun checklists(segments: List<Segment>) =
        flatten(segments).map { it.kind }.filterIsInstance<SegmentKind.Checklist>()

    /** Ranges and kinds of the whole tree, optionally with one checklist's state flipped. */
    private fun shape(segments: List<Segment>, flipAt: TextRange? = null): List<Any> =
        segments.map {
            val kind = it.kind
            val effective = if (kind is SegmentKind.Checklist &&
                kind.box == flipAt
            ) {
                kind.copy(checked = !kind.checked)
            } else {
                kind
            }
            Triple(it.range, effective, shape(it.children, flipAt))
        }

    companion object {
        private const val SEED = 7_654_321
        private const val ITERATIONS = 400
        private const val MAX_PIECES = 30

        @JvmStatic
        fun corpus(): List<String> = Corpus.names
    }
}
