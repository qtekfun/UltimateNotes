// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

/**
 * Helpers around a note's title (SPEC §4). The stored title is a separate field of the note; the
 * first body line is only used ONCE, to propose a title for a new note the user left untitled
 * ([initialTitle]), and to decide what a list row previews ([preview]).
 */
object NoteSummary {
    private const val DEFAULT_PREVIEW_LENGTH = 120

    /** The server keeps at most this many characters of a title. */
    const val MAX_TITLE_LENGTH = 100

    /** Characters the server strips from titles because they are illegal in file names. */
    private val SERVER_STRIPPED = Regex("""[*|/\\:"<>?]""")

    private val WHITESPACE = Regex("""\s+""")

    /** The first line with content, stripped of heading, list and checklist markers; "" if none. */
    fun firstLine(text: String): String = contentLines(text).firstOrNull().orEmpty()

    /**
     * The title proposed for a new note whose title the user left empty: its first line with
     * content, cut to [MAX_TITLE_LENGTH] (never splitting a surrogate pair). "" if the text has
     * none; the caller then falls back to a localized "New note".
     */
    fun initialTitle(text: String): String {
        val cut = firstLine(text).take(MAX_TITLE_LENGTH)
        return if (cut.isNotEmpty() && cut.last().isHighSurrogate()) cut.dropLast(1) else cut
    }

    /**
     * The body as a list row previews it: without its first line when that line is the [title]
     * (what the web UI shows for a note titled after its first line), otherwise the whole body.
     * Lines are joined with one space, at most [maxLength] characters (never splitting a
     * surrogate pair). No ellipsis is added: that is a UI concern.
     */
    fun preview(text: String, title: String, maxLength: Int = DEFAULT_PREVIEW_LENGTH): String {
        require(maxLength >= 0) { "maxLength must not be negative" }
        val lines = contentLines(text).toList()
        val skipFirst = lines.isNotEmpty() && repeatsTitle(lines.first(), title)
        val joined = lines.drop(if (skipFirst) 1 else 0).joinToString(" ")
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

    /**
     * Whether [line] is [title] as the server stores it: it strips some characters and cuts the
     * title at [MAX_TITLE_LENGTH], so the comparison tolerates both.
     */
    private fun repeatsTitle(line: String, title: String): Boolean {
        val wanted = comparable(title)
        if (wanted.isEmpty()) return false
        val actual = comparable(line)
        return actual == wanted || (wanted.length >= MAX_TITLE_LENGTH && actual.startsWith(wanted))
    }

    private fun comparable(text: String): String = text.replace(PlainText.LEADING_SYNTAX, "")
        .replace(SERVER_STRIPPED, "").replace(WHITESPACE, " ").trim()

    private fun contentLines(text: String): Sequence<String> = PlainText.lines(text)
}
