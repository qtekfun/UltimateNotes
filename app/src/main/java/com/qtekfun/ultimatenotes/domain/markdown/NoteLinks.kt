// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

import com.qtekfun.ultimatenotes.domain.TextRange

/** A link found in a note: where it is in the source and where it points. */
data class LinkHit(val range: TextRange, val destination: String) {
    /** Never prints the destination, which comes from a note. */
    override fun toString(): String = "LinkHit"
}

/**
 * Which link a position of a note is in. Covers what the analyzer marks as a link: inline links
 * `[text](url)`, reference links with a definition, and autolinks `<https://...>`. Bare URLs in
 * running text and images are not links for the analyzer and so are not covered.
 */
object NoteLinks {
    /**
     * The link whose source range contains [offset] (an offset between characters). With
     * [endInclusive] the position right after the link counts too, as for a caret; where two links
     * touch, the one that starts at [offset] wins.
     */
    fun linkAt(segments: List<Segment>, offset: Int, endInclusive: Boolean): LinkHit? =
        segments.fold<Segment, LinkHit?>(null) { found, segment ->
            val kind = segment.kind
            val here = if (kind is SegmentKind.Link && segment.range.covers(offset, endInclusive)) {
                LinkHit(segment.range, kind.destination)
            } else {
                linkAt(segment.children, offset, endInclusive)
            }
            here ?: found
        }

    /**
     * The openable destination (see [LinkTarget]) of the link the caret is in, or null if the
     * selection is not collapsed, the caret is not in a link, or its destination is refused.
     */
    fun openableAtCaret(text: String, selectionStart: Int, selectionEnd: Int): String? =
        if (selectionStart != selectionEnd) null else openableAt(text, selectionStart, true)

    /** The openable destination of the link that contains the character at [index], if any. */
    fun openableAtChar(text: String, index: Int): String? = openableAt(text, index, false)

    private fun openableAt(text: String, offset: Int, endInclusive: Boolean): String? =
        linkAt(MarkdownAnalyzer.analyze(text), offset, endInclusive)
            ?.let { LinkTarget.openable(it.destination) }

    private fun TextRange.covers(offset: Int, endInclusive: Boolean): Boolean =
        offset in start until end || (endInclusive && offset == end)
}
