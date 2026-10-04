// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

import com.qtekfun.ultimatenotes.domain.TextRange

/**
 * Test notation for text with a selection: `|` is a caret, `‹` and `›` delimit a selection.
 * (Markdown itself never uses these, so they cannot clash with the text under test.)
 */
internal data class Marked(val text: String, val selection: TextRange)

internal fun marked(source: String): Marked {
    val open = source.indexOf('‹')
    if (open >= 0) {
        val close = source.indexOf('›')
        val plain = source.replace("‹", "").replace("›", "")
        return Marked(plain, TextRange(open, close - 1))
    }
    val caret = source.indexOf('|')
    require(caret >= 0) { "No selection marker in: $source" }
    return Marked(source.removeRange(caret, caret + 1), TextRange(caret, caret))
}

/** Renders [text] with [selection] in the notation above. */
internal fun render(text: String, selection: TextRange): String {
    val s = selection.start
    val e = selection.end
    return if (s == e) {
        text.substring(0, s) + "|" + text.substring(s)
    } else {
        text.substring(0, s) + "‹" + text.substring(s, e) + "›" + text.substring(e)
    }
}

internal fun EditResult.render(): String = render(text, selection)

internal fun inline(source: String, style: InlineStyle): String {
    val m = marked(source)
    return toggleInline(m.text, m.selection, style).render()
}

internal fun block(source: String, kind: BlockKind): String {
    val m = marked(source)
    return setBlock(m.text, m.selection, kind).render()
}

internal fun enter(source: String): String? {
    val m = marked(source)
    return continueList(m.text, m.selection)?.render()
}
