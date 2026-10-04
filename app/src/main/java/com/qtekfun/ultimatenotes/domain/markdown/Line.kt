// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

/** A line of text: [start, end) without its terminator; [next] is where the following line starts. */
internal data class Line(val start: Int, val end: Int, val next: Int)

private fun isTerminator(c: Char) = c == '\n' || c == '\r'

/** The line containing [offset]; the offset just after a terminator belongs to the next line. */
internal fun lineAt(text: String, offset: Int): Line {
    var start = offset
    while (start > 0 && !isTerminator(text[start - 1])) start--
    var end = offset
    while (end < text.length && !isTerminator(text[end])) end++
    val next = when {
        end == text.length -> end
        text[end] == '\r' && text.getOrNull(end + 1) == '\n' -> end + 2
        else -> end + 1
    }
    return Line(start, end, next)
}

/**
 * The lines from the one holding [from] to the one holding [to]. A non-empty range that ends
 * exactly at a line start does not include that line.
 */
internal fun linesBetween(text: String, from: Int, to: Int): List<Line> {
    val result = mutableListOf(lineAt(text, from))
    while (true) {
        val last = result.last()
        val next = last.next
        val endsBeforeNext = next > to || (next == to && to > from)
        if (next == last.end || endsBeforeNext) return result
        result += lineAt(text, next)
    }
}
