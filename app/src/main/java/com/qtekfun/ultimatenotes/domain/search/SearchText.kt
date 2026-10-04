// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.search

import java.text.Normalizer
import java.util.Locale

/** A word of a text: where it sits in the original (UTF-16 offsets) and its folded form. */
internal data class Word(val start: Int, val end: Int, val folded: String)

/**
 * Text rules shared by the query and the highlighter, mirroring the FTS tokenizer (`unicode61`):
 * words are runs of letters and digits, and matching ignores case and accents.
 */
internal object SearchText {
    private val combining = Regex("\\p{M}+")

    /** Lower case with accents removed: `Café` and `cafe` fold to the same string. */
    fun fold(text: String): String = combining.replace(
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD),
        ""
    )

    /** The folded words of [text], in order. Punctuation, emoji and whitespace only separate. */
    fun words(text: String): List<Word> {
        val words = mutableListOf<Word>()
        var start = -1
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (isWordPart(codePoint)) {
                if (start < 0) start = index
            } else if (start >= 0) {
                words += word(text, start, index)
                start = -1
            }
            index += Character.charCount(codePoint)
        }
        if (start >= 0) words += word(text, start, text.length)
        return words.filter { it.folded.isNotEmpty() }
    }

    private fun word(text: String, start: Int, end: Int) =
        Word(start, end, fold(text.substring(start, end)))

    private fun isWordPart(codePoint: Int): Boolean = when (Character.getType(codePoint).toByte()) {
        Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER,
        Character.MODIFIER_LETTER, Character.OTHER_LETTER, Character.DECIMAL_DIGIT_NUMBER,
        Character.LETTER_NUMBER, Character.OTHER_NUMBER, Character.NON_SPACING_MARK,
        Character.COMBINING_SPACING_MARK, Character.PRIVATE_USE -> true

        else -> false
    }
}
