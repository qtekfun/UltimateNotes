// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

import com.qtekfun.ultimatenotes.domain.TextRange

/** How one piece of the selection is changed. */
private sealed interface Op {
    data object Keep : Op

    data object Wrap : Op

    /** The markers sit just outside the piece. */
    data object UnwrapOutside : Op

    /** The markers are the piece's own first and last characters. */
    data object UnwrapInside : Op
}

/** One trimmed, single-line piece of the selection with what to do to it. */
private data class Piece(val start: Int, val end: Int, val op: Op)

/**
 * Toggles [style] on the selection: wraps it in markers, or removes them when it already has the
 * style. Every line of a multi-line selection is wrapped on its own, so the markup stays valid.
 * With a caret only, an empty marker pair is inserted (or an empty pair around the caret removed).
 * Only the markers change; the text between them is untouched.
 */
@Suppress("ReturnCount")
fun toggleInline(text: String, selection: TextRange, style: InlineStyle): EditResult {
    require(selection.end <= text.length) { "Selection out of bounds" }
    if (selection.start == selection.end) return toggleAtCaret(text, selection.start, style)
    val candidates = linesBetween(text, selection.start, selection.end).mapNotNull { line ->
        trimmed(text, maxOf(line.start, selection.start), minOf(line.end, selection.end))
    }
    if (candidates.isEmpty()) return EditResult(text, selection)
    val allStyled = candidates.all { styledState(text, it.first, it.second, style) != null }
    val pieces = candidates.map { (start, end) ->
        val state = styledState(text, start, end, style)
        Piece(
            start,
            end,
            if (allStyled) {
                state!!
            } else if (state != null) {
                Op.Keep
            } else {
                Op.Wrap
            }
        )
    }
    return apply(text, pieces, style)
}

/** The piece without surrounding whitespace, or null when nothing is left. */
private fun trimmed(text: String, from: Int, to: Int): Pair<Int, Int>? {
    var start = from
    var end = to
    while (start < end && text[start].isWhitespace()) start++
    while (end > start && text[end - 1].isWhitespace()) end--
    return if (start < end) start to end else null
}

/** The unwrap operation if [start, end) already has [style], else null. */
private fun styledState(text: String, start: Int, end: Int, style: InlineStyle): Op? {
    val outside = style.isActiveFor(style.runBefore(text, start)) &&
        style.isActiveFor(style.runAfter(text, end))
    if (outside) return Op.UnwrapOutside
    val lead = style.runAfter(text, start, end)
    val trail = style.runBefore(text, end).coerceAtMost(end - start)
    val hasContent = lead + trail < end - start
    return if (hasContent && style.isActiveFor(lead) &&
        style.isActiveFor(trail)
    ) {
        Op.UnwrapInside
    } else {
        null
    }
}

private fun apply(text: String, pieces: List<Piece>, style: InlineStyle): EditResult {
    // Wrapping adds the marker, unwrapping removes one: `***x***` (bold italic) loses only one style.
    val marker = style.marker
    val m = marker.length
    val out = StringBuilder()
    var cursor = 0
    var selStart = -1
    var selEnd = 0
    for ((start, end, op) in pieces) {
        when (op) {
            Op.Wrap -> {
                out.append(text, cursor, start).append(marker)
                if (selStart < 0) selStart = out.length
                out.append(text, start, end)
                selEnd = out.length
                out.append(marker)
                cursor = end
            }

            Op.UnwrapOutside -> {
                out.append(text, cursor, start - m)
                if (selStart < 0) selStart = out.length
                out.append(text, start, end)
                selEnd = out.length
                cursor = end + m
            }

            Op.UnwrapInside -> {
                out.append(text, cursor, start)
                if (selStart < 0) selStart = out.length
                out.append(text, start + m, end - m)
                selEnd = out.length
                cursor = end
            }

            Op.Keep -> {
                out.append(text, cursor, start)
                if (selStart < 0) selStart = out.length
                out.append(text, start, end)
                selEnd = out.length
                cursor = end
            }
        }
    }
    out.append(text, cursor, text.length)
    return EditResult(out.toString(), TextRange(selStart, selEnd))
}

private fun toggleAtCaret(text: String, caret: Int, style: InlineStyle): EditResult {
    val m = style.marker.length
    val pairAround = caret >= m && text.startsWith(style.marker, caret - m) &&
        text.startsWith(style.marker, caret) &&
        style.isActiveFor(style.runBefore(text, caret)) &&
        style.isActiveFor(style.runAfter(text, caret))
    return if (pairAround) {
        EditResult(text.removeRange(caret - m, caret + m), TextRange(caret - m, caret - m))
    } else {
        val result = text.substring(0, caret) + style.marker + style.marker + text.substring(caret)
        EditResult(result, TextRange(caret + m, caret + m))
    }
}

/**
 * Turns the selection into a link: `[text]()` with the caret in the parentheses, or, when the
 * selection is a URL, `[](url)` with the caret in the brackets. With a caret only, `[]()`.
 */
fun insertLink(text: String, selection: TextRange): EditResult {
    require(selection.end <= text.length) { "Selection out of bounds" }
    val selected = text.substring(selection.start, selection.end)
    val isUrl = URL.matches(selected)
    val label = if (isUrl) "" else selected
    val target = if (isUrl) selected else ""
    val link = "[$label]($target)"
    val caret =
        selection.start +
            if (isUrl || selected.isEmpty()) 1 else label.length + LINK_PREFIX_AND_OPEN
    val result = text.substring(0, selection.start) + link + text.substring(selection.end)
    return EditResult(result, TextRange(caret, caret))
}

/** `[` before the label, `](` after it: where the caret goes in a link made from a text selection. */
private const val LINK_PREFIX_AND_OPEN = 3

private val URL = Regex("""https?://\S+""")
