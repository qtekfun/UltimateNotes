// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.search

import com.qtekfun.ultimatenotes.data.local.model.SearchMarkers
import com.qtekfun.ultimatenotes.domain.TextRange

/** A text to show on one line or two, with the parts that matched the search. */
data class Highlighted(val text: String, val ranges: List<TextRange>) {
    companion object {
        val EMPTY = Highlighted("", emptyList())
    }
}

/** Pure functions that work out what to highlight; the UI only paints the ranges. */
object Highlight {
    /**
     * The words of [text] that [query] matched, as ranges of [text]: whole words for the first
     * terms, and the typed part of the word for the last term, which matches as a prefix.
     * Accents and case are ignored. Offsets are UTF-16 and never split a surrogate pair.
     */
    fun matches(text: String, query: SearchQuery): List<TextRange> {
        val last = query.terms.lastIndex
        return SearchText.words(text).mapNotNull { word ->
            val matched = query.terms.withIndex()
                .filter { (index, term) ->
                    word.folded == term || (index == last && word.folded.startsWith(term))
                }
                .maxOfOrNull { it.value.length }
            matched?.let { TextRange(word.start, end(text, word, it)) }
        }
    }

    /** Where the first [foldedLength] folded characters of [word] end in the original text. */
    private fun end(text: String, word: Word, foldedLength: Int): Int {
        var folded = 0
        var index = word.start
        while (index < word.end && folded < foldedLength) {
            val codePoint = text.codePointAt(index)
            index += Character.charCount(codePoint)
            // Combining marks fold to nothing, so they stay with the letter before them.
            folded += SearchText.fold(String(Character.toChars(codePoint))).length
        }
        while (index < word.end && SearchText.fold(text.substring(index, index + 1)).isEmpty()) {
            index++
        }
        return index
    }

    /**
     * Reads a snippet from the search engine, where matches sit between [SearchMarkers.OPEN] and
     * [SearchMarkers.CLOSE]. The markers are removed and every run of whitespace, line breaks
     * (also CRLF) included, becomes one space so the snippet fits a row. Unbalanced markers are
     * tolerated: a match left open ends with the text.
     */
    fun snippet(marked: String): Highlighted {
        val out = StringBuilder()
        val ranges = mutableListOf<TextRange>()
        var open = -1
        for (char in marked) {
            when {
                char == SearchMarkers.OPEN[0] -> open = out.length

                char == SearchMarkers.CLOSE[0] -> {
                    if (open in 0 until out.length) ranges += TextRange(open, out.length)
                    open = -1
                }

                char.isWhitespace() -> if (out.isNotEmpty() && out.last() != ' ') out.append(' ')

                else -> out.append(char)
            }
        }
        if (open in 0 until out.length) ranges += TextRange(open, out.length)
        val trimmed = out.trimEnd().toString()
        return Highlighted(
            trimmed,
            ranges.mapNotNull { range ->
                val end = minOf(range.end, trimmed.length)
                var start = range.start
                while (start < end && trimmed[start] == ' ') start++
                if (end > start) TextRange(start, end) else null
            }
        )
    }
}
