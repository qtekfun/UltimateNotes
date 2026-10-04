// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.export

import com.qtekfun.ultimatenotes.domain.TextRange
import com.qtekfun.ultimatenotes.domain.markdown.InlineStyle

/** An inline [style] over [range] of a block's [ExportBlock.text]. */
data class StyleSpan(val range: TextRange, val style: InlineStyle)

enum class ExportBlockKind { Title, Heading, Body }

/**
 * One source line of a note, ready to be laid out: [text] has the Markdown syntax removed and
 * [spans] style it. A list item has a [marker] (bullet, number or checkbox glyph); [indent] is
 * the list nesting depth and [quoteDepth] the number of quote bars. [level] is the heading level.
 */
data class ExportBlock(
    val kind: ExportBlockKind,
    val text: String,
    val spans: List<StyleSpan> = emptyList(),
    val level: Int = 0,
    val marker: String? = null,
    val indent: Int = 0,
    val quoteDepth: Int = 0
)
