// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

/** Inline styles the formatting bar can toggle. [marker] is what is written around the text. */
enum class InlineStyle(val marker: String) {
    Bold("**"),
    Italic("*"),
    Strike("~~"),
    Code("`");

    private val markerChar get() = marker[0]

    /** Whether a run of [run] consecutive marker characters carries this style (`***` is bold italic). */
    internal fun isActiveFor(run: Int): Boolean = when (this) {
        Italic -> run % 2 == 1
        else -> run >= marker.length
    }

    internal fun runBefore(text: String, offset: Int): Int {
        var n = 0
        while (offset - n > 0 && text[offset - n - 1] == markerChar) n++
        return n
    }

    internal fun runAfter(text: String, offset: Int, limit: Int = text.length): Int {
        var n = 0
        while (offset + n < limit && text[offset + n] == markerChar) n++
        return n
    }
}
