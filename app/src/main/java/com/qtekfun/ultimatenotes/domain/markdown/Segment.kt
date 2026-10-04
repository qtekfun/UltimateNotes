// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

import com.qtekfun.ultimatenotes.domain.TextRange

/**
 * A styled region of the Markdown source. Ranges always refer to the original text, which is
 * never rewritten: syntax the editor does not support is simply an [SegmentKind.Opaque] range
 * that gets no styling, so it survives a save byte for byte.
 *
 * Segments are ordered by start offset and children lie inside their parent's range.
 */
data class Segment(
    val range: TextRange,
    val kind: SegmentKind,
    val children: List<Segment> = emptyList()
)

/** What a [Segment] is. */
sealed interface SegmentKind {
    data class Heading(val level: Int) : SegmentKind

    data object Quote : SegmentKind

    /** A list item, bulleted or (when [ordered]) numbered, whose range includes nested items. */
    data class ListItem(val ordered: Boolean) : SegmentKind

    /** A list item written as `- [ ]` / `- [x]`; [box] is the `[ ]` / `[x]` in the source. */
    data class Checklist(val checked: Boolean, val box: TextRange) : SegmentKind

    data object Bold : SegmentKind

    data object Italic : SegmentKind

    data object Strike : SegmentKind

    /** Inline code, backticks included. */
    data object Code : SegmentKind

    data class Link(val destination: String) : SegmentKind {
        /** Never prints the destination, which comes from a note. */
        override fun toString(): String = "Link"
    }

    /** Syntax the editor does not model (tables, HTML, code blocks, images...); leave untouched. */
    data object Opaque : SegmentKind
}
