// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.local

/** Turns what the user typed into a safe FTS MATCH expression. */
object FtsQuery {
    private val whitespace = Regex("""\s+""")

    /**
     * Every word becomes a quoted prefix term (`"wor*"`, FTS4 syntax), so FTS operators typed by the user are
     * plain text and the last word matches while it is still being typed. Returns null when there
     * is nothing to search for.
     */
    fun fromUserInput(input: String): String? {
        // Double quotes are punctuation to the tokenizer, so dropping them loses nothing.
        val terms = input.replace("\"", " ").split(whitespace).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return null
        return terms.joinToString(" ") { "\"$it*\"" }
    }
}
