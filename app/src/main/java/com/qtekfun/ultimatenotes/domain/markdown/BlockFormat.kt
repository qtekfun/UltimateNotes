// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

import com.qtekfun.ultimatenotes.domain.TextRange

/** The block marker at the start of a line: leading [indent] characters, then [markerLength] of marker. */
private data class BlockPrefix(val kind: BlockKind?, val indent: Int, val markerLength: Int)

private val BLOCK_PREFIX = Regex(
    "^(?<indent>[ \\t]*)(?:(?<heading>#{1,6})(?:[ \\t]+|$)|(?<quote>(?:>[ \\t]?)+)|" +
        "[-*+](?:[ \\t]+|$)(?<box>\\[[ xX]](?:[ \\t]+|$))?|(?<number>\\d{1,9})[.)](?:[ \\t]+|$))"
)

private fun prefixOf(text: String, line: Line): BlockPrefix {
    val match = BLOCK_PREFIX.find(text.substring(line.start, line.end))
        ?: return BlockPrefix(
            null,
            text.substring(line.start, line.end).takeWhile(Char::isWhitespace).length,
            0
        )
    val indent = match.groups["indent"]!!.value.length
    val kind = when {
        match.groups["heading"] != null -> BlockKind.Heading(match.groups["heading"]!!.value.length)
        match.groups["quote"] != null -> BlockKind.Quote
        match.groups["box"] != null -> BlockKind.Checklist
        match.groups["number"] != null -> BlockKind.Numbered
        else -> BlockKind.Bullet
    }
    return BlockPrefix(kind, indent, match.value.length - indent)
}

/** The block format of the line holding the start of [selection], or null for plain text. */
fun blockAt(text: String, selection: TextRange): BlockKind? {
    require(selection.end <= text.length) { "Selection out of bounds" }
    return prefixOf(text, lineAt(text, selection.start)).kind
}

/**
 * Sets [block] on every line the selection touches (blank lines are skipped when there are
 * several), replacing whatever block marker they had; when they all have it already, removes it.
 * Numbered lists count from 1. Only the markers change.
 */
fun setBlock(text: String, selection: TextRange, block: BlockKind): EditResult {
    require(selection.end <= text.length) { "Selection out of bounds" }
    val all = linesBetween(text, selection.start, selection.end)
    val lines = if (all.size > 1) all.filter { it.start != it.end } else all
    if (lines.isEmpty()) return EditResult(text, selection)
    val prefixes = lines.map { prefixOf(text, it) }
    val remove = prefixes.all { it.kind == block }
    val out = StringBuilder()
    val changes = mutableListOf<Triple<Line, BlockPrefix, Int>>()
    var cursor = 0
    var number = 1
    for ((line, prefix) in lines.zip(prefixes)) {
        val marker = if (remove) "" else markerFor(block, number++)
        out.append(text, cursor, line.start + prefix.indent).append(marker)
        cursor = line.start + prefix.indent + prefix.markerLength
        changes += Triple(line, prefix, marker.length)
    }
    out.append(text, cursor, text.length)
    return EditResult(
        out.toString(),
        TextRange(mapOffset(selection.start, changes), mapOffset(selection.end, changes))
    )
}

private fun markerFor(block: BlockKind, number: Int): String = when (block) {
    is BlockKind.Heading -> "#".repeat(block.level) + " "
    BlockKind.Quote -> "> "
    BlockKind.Bullet -> "- "
    BlockKind.Numbered -> "$number. "
    BlockKind.Checklist -> "- [ ] "
}

/** Where [offset] lands once each line's marker was replaced by one of the lengths in [changes]. */
private fun mapOffset(offset: Int, changes: List<Triple<Line, BlockPrefix, Int>>): Int {
    var shift = 0
    for ((line, prefix, newLength) in changes) {
        val markerStart = line.start + prefix.indent
        val markerEnd = markerStart + prefix.markerLength
        if (offset < markerStart) break
        if (offset < markerEnd) return markerStart + shift + minOf(offset - markerStart, newLength)
        shift += newLength - prefix.markerLength
    }
    return offset + shift
}
