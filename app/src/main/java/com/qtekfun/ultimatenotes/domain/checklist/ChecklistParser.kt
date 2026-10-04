// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.checklist

import com.qtekfun.ultimatenotes.domain.TextRange

/**
 * Line-based checklist support: `- [ ]` / `- [x]` (also `*` and `+` markers, any indentation, inside quotes).
 *
 * It is purely lexical and never reserializes: toggling changes exactly one character. It does
 * not know about code fences; callers that must ignore them (the editor) take their offsets
 * from the Markdown analyzer's checklist segments.
 */
object ChecklistParser {
    private val ITEM = Regex("""(?:[ \t]*>)*[ \t]*[-*+][ \t]+\[[ xX]](?=[ \t\r\n]|$)""")

    private const val UNCHECKED = ' '
    private const val CHECKED = 'x'

    /** The checklist item whose line (or list marker) starts at [from], or null if it is not one. */
    fun parseAt(text: String, from: Int): ChecklistItem? {
        val match = ITEM.matchAt(text, from) ?: return null
        // The match ends with the three-character box (the lookahead consumes nothing).
        val start = match.range.last + 1 - BOX_LENGTH
        return ChecklistItem(TextRange(start, start + BOX_LENGTH), text[start + 1] != UNCHECKED)
    }

    /** Every checklist line of [text], in order. */
    fun parse(text: String): List<ChecklistItem> =
        lineStarts(text).mapNotNull { parseAt(text, it) }.toList()

    /** Toggles the checklist on line number [line] (0-based); null if that line is not one. */
    fun toggleLine(text: String, line: Int): String? {
        require(line >= 0) { "Line out of bounds" }
        return lineStarts(text).drop(line).firstOrNull()?.let { toggleAt(text, it) }
    }

    /** Toggles the checklist on the line containing [offset]; null if that line is not one. */
    fun toggleAtOffset(text: String, offset: Int): String? {
        require(offset in 0..text.length) { "Offset out of bounds" }
        var start = offset
        while (start > 0 && text[start - 1] != '\n' && text[start - 1] != '\r') start--
        return toggleAt(text, start)
    }

    /**
     * Toggles the box at [box] (a [ChecklistItem.box], e.g. from the analyzer's checklist
     * segment). Unlike the line-based functions it works wherever the item sits (in quotes,
     * inside other list items). Changes exactly one character: `[ ]` becomes `[x]` and
     * `[x]`/`[X]` become `[ ]`.
     */
    fun toggle(text: String, box: TextRange): String {
        require(box.end - box.start == BOX_LENGTH && box.end <= text.length) {
            "Not a checklist box"
        }
        val state = text[box.start + 1]
        require(text[box.start] == '[' && text[box.end - 1] == ']' && state in " xX") {
            "Not a checklist box"
        }
        val flipped = if (state == UNCHECKED) CHECKED else UNCHECKED
        return text.substring(0, box.start + 1) + flipped + text.substring(box.end - 1)
    }

    private fun toggleAt(text: String, lineStart: Int): String? =
        parseAt(text, lineStart)?.let { toggle(text, it.box) }

    /** Offsets at which a line starts; `\r\n`, `\n` and `\r` all end a line. */
    private fun lineStarts(text: String): Sequence<Int> = sequence {
        var start = 0
        while (true) {
            yield(start)
            var i = start
            while (i < text.length && text[i] != '\n' && text[i] != '\r') i++
            if (i == text.length) break
            start = if (text[i] == '\r' && text.getOrNull(i + 1) == '\n') i + 2 else i + 1
        }
    }

    private const val BOX_LENGTH = 3
}
