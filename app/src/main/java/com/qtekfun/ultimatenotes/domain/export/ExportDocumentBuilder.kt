// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.export

import com.qtekfun.ultimatenotes.domain.TextRange
import com.qtekfun.ultimatenotes.domain.markdown.InlineStyle
import com.qtekfun.ultimatenotes.domain.markdown.MarkdownAnalyzer
import com.qtekfun.ultimatenotes.domain.markdown.NoteSummary
import com.qtekfun.ultimatenotes.domain.markdown.Segment
import com.qtekfun.ultimatenotes.domain.markdown.SegmentKind

/**
 * Turns a note into the blocks a PDF is drawn from, using the editor's own [MarkdownAnalyzer]
 * segments so the export matches what the editor styles. Each source line becomes one block with
 * its syntax removed; what the editor does not model (tables, code blocks, HTML...) is kept as
 * plain text, exactly as written.
 */
object ExportDocumentBuilder {
    const val CHECKED = "☑"
    const val UNCHECKED = "☐"
    const val BULLET = "•"

    private val QUOTE_PREFIX = Regex("""^[ \t]{0,3}>[ \t]?""")
    private val LIST_MARKER = Regex("""^[ \t]*(?:[-*+]|(\d{1,9})[.)])[ \t]*(?:\[[ xX]][ \t]*)?""")
    private val ATX_OPEN = Regex("""^[ \t]{0,3}#{1,6}(?:[ \t]+|$)""")
    private val ATX_CLOSE = Regex("""[ \t]+#+[ \t]*$""")
    private val SETEXT_UNDERLINE = Regex("""^[ \t]*(?:=+|-+)[ \t]*$""")

    /**
     * The blocks of a note. [title] is shown first, in title style: when it is the note's first
     * line that line becomes the title, otherwise a title block is added in front.
     */
    fun build(title: String, content: String): List<ExportBlock> {
        val text = content.replace("\r\n", "\n").replace('\r', '\n')
        val body = Lines(text).blocks().trimBlank()
        val heading = title.trim().ifEmpty { NoteSummary.title(text) }
        if (heading.isEmpty()) return body
        val first = body.firstOrNull()
        val isTitleLine = first != null && first.marker == null && first.quoteDepth == 0 &&
            first.kind != ExportBlockKind.Title && first.text.trim() == heading
        return if (isTitleLine) {
            listOf(first.copy(kind = ExportBlockKind.Title, level = 0)) + body.drop(1)
        } else {
            listOf(ExportBlock(ExportBlockKind.Title, heading)) + body
        }
    }

    private fun List<ExportBlock>.trimBlank(): List<ExportBlock> =
        dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }

    private fun ExportBlock.isBlank() = kind == ExportBlockKind.Body && text.isBlank()

    private fun styleOf(kind: SegmentKind): InlineStyle? = when (kind) {
        SegmentKind.Bold -> InlineStyle.Bold
        SegmentKind.Italic -> InlineStyle.Italic
        SegmentKind.Strike -> InlineStyle.Strike
        SegmentKind.Code -> InlineStyle.Code
        else -> null
    }

    /** What can delimit [style] in the source (emphasis may use `*` or `_`). */
    private fun markerChars(style: InlineStyle) = when (style) {
        InlineStyle.Bold, InlineStyle.Italic -> "*_"
        InlineStyle.Strike -> "~"
        InlineStyle.Code -> "`"
    }

    /** A code span is delimited by a run of backticks of any length. */
    private fun maxMarker(style: InlineStyle) =
        if (style == InlineStyle.Code) Int.MAX_VALUE else style.marker.length

    /** The analysis of one note's text and the walk over its lines. */
    private class Lines(private val text: String) {
        private val blockSegments = mutableListOf<Segment>()
        private val inlineSegments = mutableListOf<Segment>()

        init {
            collect(MarkdownAnalyzer.analyze(text))
        }

        private fun collect(segments: List<Segment>) {
            for (segment in segments) {
                when (segment.kind) {
                    SegmentKind.Bold, SegmentKind.Italic, SegmentKind.Strike, SegmentKind.Code,
                    is SegmentKind.Link -> inlineSegments += segment

                    else -> blockSegments += segment
                }
                collect(segment.children)
            }
        }

        fun blocks(): List<ExportBlock> {
            val result = mutableListOf<ExportBlock>()
            var start = 0
            while (true) {
                val newline = text.indexOf('\n', start)
                val end = if (newline < 0) text.length else newline
                blockOf(start, end)?.let(result::add)
                if (newline < 0) return result
                start = newline + 1
            }
        }

        private fun blockOf(start: Int, end: Int): ExportBlock? {
            val covering = blockSegments.filter { it.range.start <= end && it.range.end > start }
            return when {
                covering.any { it.kind == SegmentKind.Opaque } ->
                    ExportBlock(ExportBlockKind.Body, text.substring(start, end))

                isSetextUnderline(covering, start, end) -> null

                else -> styledBlock(covering, start, end)
            }
        }

        private fun isSetextUnderline(covering: List<Segment>, start: Int, end: Int): Boolean {
            val heading = covering.firstOrNull { it.kind is SegmentKind.Heading } ?: return false
            val pos = afterQuotes(covering, start, end)
            return heading.range.start < start && SETEXT_UNDERLINE.matches(text.substring(pos, end))
        }

        private fun afterQuotes(covering: List<Segment>, start: Int, end: Int): Int {
            var pos = start
            repeat(covering.count { it.kind == SegmentKind.Quote }) {
                pos += QUOTE_PREFIX.find(text.substring(pos, end))?.value?.length ?: 0
            }
            return pos
        }

        private fun styledBlock(covering: List<Segment>, start: Int, end: Int): ExportBlock {
            val lists = covering.filter {
                it.kind is SegmentKind.ListItem ||
                    it.kind is SegmentKind.Checklist
            }
            val afterList = afterListMarker(lists, afterQuotes(covering, start, end), start, end)
            val heading = covering.firstOrNull { it.kind is SegmentKind.Heading }
            var pos = afterList.first
            var contentEnd = end
            if (heading != null) {
                ATX_OPEN.find(text.substring(pos, end))?.let { open ->
                    pos += open.value.length
                    ATX_CLOSE.find(text.substring(pos, end))?.let {
                        contentEnd =
                            pos + it.range.first
                    }
                }
            }
            val (visible, spans) = inline(pos, contentEnd)
            return ExportBlock(
                kind = if (heading != null) ExportBlockKind.Heading else ExportBlockKind.Body,
                text = visible,
                spans = spans,
                level = (heading?.kind as? SegmentKind.Heading)?.level ?: 0,
                marker = afterList.second,
                indent = (lists.size - 1).coerceAtLeast(0),
                quoteDepth = covering.count { it.kind == SegmentKind.Quote }
            )
        }

        /** Where the content starts after the list marker (if the line has one) and the marker to draw. */
        private fun afterListMarker(
            lists: List<Segment>,
            pos: Int,
            start: Int,
            end: Int
        ): Pair<Int, String?> {
            val opener = lists.filter {
                it.range.start in start..end
            }.maxByOrNull { it.range.start }
            val rest = text.substring(pos, end)
            val match = LIST_MARKER.find(rest)
            return when {
                lists.isEmpty() -> pos to null

                opener == null -> pos + rest.takeWhile { it == ' ' || it == '\t' }.length to ""

                else -> pos + (match?.value?.length ?: 0) to
                    markerFor(opener, match?.groupValues?.get(1).orEmpty())
            }
        }

        private fun markerFor(item: Segment, number: String): String = when (val kind = item.kind) {
            is SegmentKind.Checklist -> if (kind.checked) CHECKED else UNCHECKED
            is SegmentKind.ListItem -> if (kind.ordered) "${number.ifEmpty { "1" }}." else BULLET
            else -> BULLET
        }

        /** The text of [from, to) without inline syntax, and the styles over it. */
        private fun inline(from: Int, to: Int): Pair<String, List<StyleSpan>> {
            val removal = Removal(text, from, to)
            val styled = mutableListOf<Pair<TextRange, InlineStyle>>()
            for (segment in inlineSegments.filter { it.range.start < to && it.range.end > from }) {
                val style = styleOf(segment.kind)
                if (style == null) {
                    removal.link(segment.range)
                } else {
                    removal.markers(segment.range, markerChars(style), maxMarker(style))
                    styled +=
                        TextRange(maxOf(segment.range.start, from), minOf(segment.range.end, to)) to
                        style
                }
            }
            val (visible, shown) = removal.visible()
            val spans = styled.mapNotNull { (range, style) ->
                val a = shown[range.start - from]
                val b = shown[range.end - from]
                if (a < b) StyleSpan(TextRange(a, b), style) else null
            }
            return visible to spans
        }
    }

    /** The characters of [from, to) of [text] that are syntax, not content. */
    private class Removal(private val text: String, private val from: Int, private val to: Int) {
        private val removed = BooleanArray(to - from)

        private fun remove(i: Int) {
            if (i in from until to) removed[i - from] = true
        }

        /** Removes up to [max] of the [chars] opening and closing the span [range], where they are in view. */
        fun markers(range: TextRange, chars: String, max: Int) {
            var i = range.start
            val highest = range.start + minOf(to - range.start, max)
            while (i >= from && i < highest && text[i] in chars) remove(i++)
            if (range.end > to) return
            var j = range.end - 1
            val lowest = maxOf(from, range.start)
            while (j >= lowest && range.end - 1 - j < max && text[j] in chars) remove(j--)
        }

        /** Turns `[label](destination)` into `label`. */
        fun link(range: TextRange) {
            val close = text.lastIndexOf("](", range.end - 1)
            val whole = range.start >= from && range.end <= to
            if (!whole || text[range.start] != '[' || close <= range.start) return
            remove(range.start)
            for (i in close until range.end) remove(i)
        }

        /** The text left, and for each offset in [from, to] where it lands in that text. */
        fun visible(): Pair<String, IntArray> {
            val shown = IntArray(removed.size + 1)
            val out = StringBuilder()
            for (i in removed.indices) {
                shown[i] = out.length
                if (!removed[i]) out.append(text[from + i])
            }
            shown[removed.size] = out.length
            return out.toString() to shown
        }
    }
}
