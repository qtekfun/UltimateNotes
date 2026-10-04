// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

import com.qtekfun.ultimatenotes.domain.TextRange

private val CONTINUATION = Regex(
    "^(?<quote>(?:[ \\t]*>[ \\t]?)*)(?<indent>[ \\t]*)" +
        "(?:(?<bullet>[-*+])(?:[ \\t]+|$)(?<box>\\[[ xX]](?:[ \\t]+|$))?|" +
        "(?<number>\\d{1,9})(?<delimiter>[.)])(?:[ \\t]+|$))?"
)

/**
 * What pressing Enter does at the caret [selection] of [text] (the text before the newline is
 * inserted) on a list, checklist or quote line, or null when the editor's plain newline should
 * be left to do its job.
 *
 * - With content after the marker, the line is split and the new line gets the next marker:
 *   same bullet, next number, an unticked box, the same quote prefix.
 * - On an item with nothing after its marker, the marker is removed instead (the list ends)
 *   and no newline is added.
 */
fun continueList(text: String, selection: TextRange): EditResult? {
    require(selection.end <= text.length) { "Selection out of bounds" }
    val line = lineAt(text, selection.start)
    val match = CONTINUATION.find(text.substring(line.start, line.end))!!
    val quote = match.groups["quote"]!!.value
    val marked = match.groups["bullet"] != null || match.groups["number"] != null
    val markerEnd = line.start + match.value.length
    val applies = selection.start == selection.end && (quote.isNotEmpty() || marked) &&
        selection.start >= markerEnd
    return when {
        !applies -> null

        text.substring(
            markerEnd,
            line.end
        ).isBlank() -> endList(text, line, if (marked) quote else "")

        else -> splitItem(text, selection.start, line, match)
    }
}

/** Empty item: leaves the list (or the quote), keeping only [kept] of the line. */
private fun endList(text: String, line: Line, kept: String): EditResult {
    val caret = line.start + kept.length
    return EditResult(
        text.substring(0, line.start) + kept + text.substring(line.end),
        TextRange(caret, caret)
    )
}

/** Breaks the line at [caret] and starts the next item of the same list on the new line. */
private fun splitItem(text: String, caret: Int, line: Line, match: MatchResult): EditResult {
    val bullet = match.groups["bullet"]?.value
    val number = match.groups["number"]?.value
    val marker = when {
        bullet != null && match.groups["box"] != null -> "$bullet [ ] "
        bullet != null -> "$bullet "
        number != null -> "${number.toLong() + 1}${match.groups["delimiter"]!!.value} "
        else -> ""
    }
    val newline = if (text.startsWith("\r\n", line.end)) "\r\n" else "\n"
    val inserted = newline + match.groups["quote"]!!.value + match.groups["indent"]!!.value + marker
    val result = text.substring(0, caret) + inserted + text.substring(caret)
    return EditResult(result, TextRange(caret + inserted.length, caret + inserted.length))
}
