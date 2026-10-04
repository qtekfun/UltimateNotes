// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

import com.qtekfun.ultimatenotes.domain.TextRange
import com.qtekfun.ultimatenotes.domain.checklist.ChecklistParser
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.LinkReferenceDefinition
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser

/**
 * Finds the styled regions of a Markdown note as source ranges.
 *
 * The editor's document *is* the Markdown source, so this only analyzes: it returns offsets into
 * the text it was given and has no way to change it.
 */
object MarkdownAnalyzer {
    private val parser: Parser = Parser.builder()
        .extensions(listOf(StrikethroughExtension.create(), TablesExtension.create()))
        .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
        .build()

    fun analyze(text: String): List<Segment> = segmentsOf(parser.parse(text), text)

    private fun segmentsOf(parent: Node, text: String): List<Segment> {
        val result = mutableListOf<Segment>()
        var child = parent.firstChild
        while (child != null) {
            result += convert(child, text)
            child = child.next
        }
        return result
    }

    /** A node without its own segment (paragraphs, lists, text) is transparent: its children surface. */
    private fun convert(node: Node, text: String): List<Segment> {
        val kind = kindOf(node, text) ?: return segmentsOf(node, text)
        val spans = node.sourceSpans
        val last = spans.last()
        val range = TextRange(spans.first().inputIndex, last.inputIndex + last.length)
        val children = if (kind == SegmentKind.Opaque) emptyList() else segmentsOf(node, text)
        return listOf(Segment(range, kind, children))
    }

    private fun kindOf(node: Node, text: String): SegmentKind? = when (node) {
        is Heading -> SegmentKind.Heading(node.level)

        is BlockQuote -> SegmentKind.Quote

        is ListItem -> listItemKind(node, text)

        is StrongEmphasis -> SegmentKind.Bold

        is Emphasis -> SegmentKind.Italic

        is Strikethrough -> SegmentKind.Strike

        is Code -> SegmentKind.Code

        is Link -> SegmentKind.Link(node.destination)

        is FencedCodeBlock, is IndentedCodeBlock, is HtmlBlock, is HtmlInline, is Image,
        is TableBlock, is ThematicBreak, is LinkReferenceDefinition -> SegmentKind.Opaque

        else -> null
    }

    private fun listItemKind(item: ListItem, text: String): SegmentKind {
        val checklist = ChecklistParser.parseAt(text, item.sourceSpans.first().inputIndex)
        return if (checklist != null) {
            SegmentKind.Checklist(checklist.checked, checklist.box)
        } else {
            SegmentKind.ListItem(ordered = item.parent is OrderedList)
        }
    }
}
