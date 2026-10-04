// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

import com.qtekfun.ultimatenotes.domain.TextRange

/** The text after a formatting operation and where the selection ends up in it. */
data class EditResult(val text: String, val selection: TextRange) {
    /**
     * The smallest single replacement that turns [original] into [text]. The editor applies this
     * instead of rewriting the whole document, so undo steps stay small and the caret is not
     * disturbed outside the edited region.
     */
    fun toEdit(original: String): TextEdit {
        val limit = minOf(original.length, text.length)
        var prefix = 0
        while (prefix < limit && original[prefix] == text[prefix]) prefix++
        // Never cut a surrogate pair in half.
        if (prefix > 0 && original[prefix - 1].isHighSurrogate()) prefix--
        var suffix = 0
        while (suffix < limit - prefix &&
            original[original.length - 1 - suffix] == text[text.length - 1 - suffix]
        ) {
            suffix++
        }
        if (suffix > 0 && text[text.length - suffix].isLowSurrogate()) suffix--
        return TextEdit(
            range = TextRange(prefix, original.length - suffix),
            replacement = text.substring(prefix, text.length - suffix),
            selection = selection
        )
    }
}

/** Replace [range] of the current text with [replacement], then select [selection] (in the new text). */
data class TextEdit(val range: TextRange, val replacement: String, val selection: TextRange) {
    fun applyTo(original: String): String =
        original.substring(0, range.start) + replacement + original.substring(range.end)
}
