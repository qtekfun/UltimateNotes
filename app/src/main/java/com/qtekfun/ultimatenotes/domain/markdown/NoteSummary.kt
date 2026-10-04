// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

/**
 * What a list row shows for a note: a title and a short preview, both derived from the Markdown
 * source (Nextcloud Notes has no separate title field the user edits in the body).
 */
object NoteSummary {
    private const val DEFAULT_PREVIEW_LENGTH = 120

    /** Leading syntax that is not content: indentation, `#`s, list markers, checklist boxes. */
    private val LEADING_SYNTAX =
        Regex(
            """^[ \t]*(?:#{1,6}(?:[ \t]+|$)|[-*+](?:[ \t]+|$)(?:\[[ xX]](?:[ \t]+|$))?|\d{1,9}[.)](?:[ \t]+|$))"""
        )

    /** The first line with content, stripped of heading, list and checklist markers; "" if none. */
    fun title(text: String): String = contentLines(text).firstOrNull().orEmpty()

    /**
     * The content after the title line, one space between lines, at most [maxLength] characters
     * (never splitting a surrogate pair). No ellipsis is added: that is a UI concern.
     */
    fun preview(text: String, maxLength: Int = DEFAULT_PREVIEW_LENGTH): String {
        require(maxLength >= 0) { "maxLength must not be negative" }
        val joined = contentLines(text).drop(1).joinToString(" ")
        if (joined.length <= maxLength) return joined
        val cut = if (maxLength > 0 &&
            joined[maxLength - 1].isHighSurrogate()
        ) {
            maxLength - 1
        } else {
            maxLength
        }
        return joined.substring(0, cut)
    }

    private fun contentLines(text: String): Sequence<String> = text.lineSequence()
        .map { it.replace(LEADING_SYNTAX, "").trim() }
        .filter { it.isNotEmpty() }
}
