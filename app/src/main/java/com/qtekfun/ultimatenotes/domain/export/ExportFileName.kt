// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.export

/** Builds a safe file name for an exported note from its title. */
object ExportFileName {
    const val FALLBACK = "note"
    const val MAX_BASE_LENGTH = 80

    private val ILLEGAL = Regex("""[\\/:*?"<>|\p{Cntrl}\p{Cf}]""")
    private val SPACES = Regex("""\s+""")

    /**
     * The title without path separators, characters that file systems reject and control
     * characters; whitespace collapsed, at most [MAX_BASE_LENGTH] characters (never splitting a
     * surrogate pair), no leading or trailing dots or spaces; [FALLBACK] if nothing is left.
     */
    fun base(title: String): String {
        val cleaned = title.replace(ILLEGAL, " ").replace(SPACES, " ").trim(' ', '.')
        val cut = if (cleaned.length > MAX_BASE_LENGTH) {
            val end = if (cleaned[MAX_BASE_LENGTH - 1].isHighSurrogate()) {
                MAX_BASE_LENGTH - 1
            } else {
                MAX_BASE_LENGTH
            }
            cleaned.substring(0, end)
        } else {
            cleaned
        }
        return cut.trim(' ', '.').ifEmpty { FALLBACK }
    }

    fun of(title: String, format: ExportFormat): String = "${base(title)}.${format.extension}"
}
