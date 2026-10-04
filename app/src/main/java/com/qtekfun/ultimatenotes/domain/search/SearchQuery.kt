// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.search

/**
 * What the user typed, reduced to the words that can be searched: lower case, without accents and
 * without punctuation, so no FTS operator or quote typed by the user can reach the engine.
 * Every word must match; the last one also matches as a prefix, because it is still being typed.
 */
class SearchQuery private constructor(val terms: List<String>) {
    /** The FTS4 MATCH expression: `"milk" "eg*"` for `milk eg`. */
    val ftsMatch: String
        get() = terms.mapIndexed { index, term ->
            if (index == terms.lastIndex) "\"$term*\"" else "\"$term\""
        }.joinToString(" ")

    override fun equals(other: Any?) = other is SearchQuery && other.terms == terms

    override fun hashCode() = terms.hashCode()

    /** Never prints what was typed: queries are private. */
    override fun toString() = "SearchQuery(${terms.size} terms)"

    companion object {
        /** More words than this are ignored: nobody types them and the query stays cheap. */
        const val MAX_TERMS = 8

        /** Longer words are cut: no real word is longer and the engine gets bounded input. */
        const val MAX_TERM_LENGTH = 64

        /** Null when [input] has nothing to search for (empty, blank or only symbols). */
        fun parse(input: String): SearchQuery? {
            val terms = SearchText.words(input)
                .map { cut(it.folded) }
                .take(MAX_TERMS)
            return if (terms.isEmpty()) null else SearchQuery(terms)
        }

        private fun cut(term: String): String {
            val cut = term.take(MAX_TERM_LENGTH)
            return if (cut.length < term.length &&
                cut.last().isHighSurrogate()
            ) {
                cut.dropLast(1)
            } else {
                cut
            }
        }
    }
}
