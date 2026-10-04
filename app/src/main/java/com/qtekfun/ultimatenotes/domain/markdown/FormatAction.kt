// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

import com.qtekfun.ultimatenotes.domain.TextRange

/** A button of the formatting bar. */
sealed interface FormatAction {
    data class Inline(val style: InlineStyle) : FormatAction

    data class Block(val kind: BlockKind) : FormatAction

    /** One button for all heading levels: plain, then `#`, `##`, `###`, then plain again. */
    data object Heading : FormatAction

    data object Link : FormatAction
}

/** Applies [action] to the selection. */
fun applyFormat(text: String, selection: TextRange, action: FormatAction): EditResult =
    when (action) {
        is FormatAction.Inline -> toggleInline(text, selection, action.style)
        is FormatAction.Block -> setBlock(text, selection, action.kind)
        FormatAction.Link -> insertLink(text, selection)
        FormatAction.Heading -> setBlock(text, selection, nextHeading(blockAt(text, selection)))
    }

private fun nextHeading(current: BlockKind?): BlockKind.Heading = when {
    current !is BlockKind.Heading -> BlockKind.Heading(1)

    // Past the third level (or on a deeper one) the same level is set again, which removes it.
    current.level >= MAX_CYCLE_LEVEL -> current

    else -> BlockKind.Heading(current.level + 1)
}

private const val MAX_CYCLE_LEVEL = 3
