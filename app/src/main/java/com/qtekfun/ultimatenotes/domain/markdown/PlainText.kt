// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

/**
 * Markdown as plain text, for the places that only read a note (list previews, search snippets,
 * proposed titles). It never changes a note: the editor works on the untouched source.
 *
 * It is deliberately forgiving: syntax it does not understand stays as it is, and unbalanced
 * markers (a snippet can cut a note in the middle of a `**`) are simply dropped.
 */
object PlainText {
    /** Leading syntax that is not content: indentation, `#`s, quotes, list markers, check boxes. */
    internal val LEADING_SYNTAX =
        Regex(
            """^[ \t]*(?:(?:>[ \t]?)+|#{1,6}(?:[ \t]+|$)""" +
                """|[-*+](?:[ \t]+|$)(?:\[[ xX]](?:[ \t]+|$))?|\d{1,9}[.)](?:[ \t]+|$))"""
        )

    /** Lines that carry no text: horizontal rules, code fences and table separator rows. */
    private val DECORATION =
        Regex(
            """^[ \t]*(?:([-*_])(?:[ \t]*\1){2,}|(?:`{3,}|~{3,})[^`]*""" +
                """|\|?[ \t]*:?-{2,}:?[ \t]*(?:\|[ \t]*:?-{2,}:?[ \t]*)*\|?)[ \t]*$"""
        )

    private val IMAGE = Regex("""!\[([^\]]*)]\([^)]*\)""")
    private val LINK = Regex("""\[([^\]]+)]\([^)]*\)""")
    private val REFERENCE_LINK = Regex("""\[([^\]]+)]\[[^\]]*]""")
    private val AUTOLINK = Regex("""<((?:https?|mailto):[^>\s]+)>""")
    private val CODE = Regex("""(`+)(.+?)\1""")
    private val BOLD = Regex("""(\*\*|__)(?=\S)(.+?)(?<=\S)\1""")
    private val STRIKE = Regex("""~~(?=\S)(.+?)(?<=\S)~~""")
    private val ITALIC_STAR = Regex("""\*(?=[^\s*])([^*]+?)(?<=[^\s*])\*""")
    private val ITALIC_UNDERSCORE =
        Regex("""(?<![\p{L}\p{N}_])_(?=[^\s_])([^_]+?)(?<=[^\s_])_(?![\p{L}\p{N}_])""")
    private val ESCAPE = Regex("""\\([\\`*_{}\[\]()#+\-.!~|>])""")

    /** Escaped characters are hidden as private-use code points while the syntax is read. */
    private const val HIDDEN_BASE = 0xE100
    private const val HIDDEN_SPAN = 0x7F
    private val STRAY_MARKS = Regex("""\*\*|~~|__""")
    private val TABLE_PIPE = Regex("""[ \t]*\|[ \t]*""")

    /** Characters that can start a rule, a fence or a table separator row. */
    private const val DECORATION_STARTS = "-*_`~|"

    /** Characters that can start inline syntax; a line without any of them needs no regex. */
    private const val INLINE_MARKS = "*_~`[<\\|"

    /** [line] as plain text, trimmed; "" when the line has no text (a rule, a fence, blank). */
    fun line(line: String): String = if (line.isBlank() || isDecoration(line)) {
        ""
    } else {
        inline(line.replace(LEADING_SYNTAX, "")).trim()
    }

    private fun isDecoration(line: String): Boolean =
        line.trimStart().first() in DECORATION_STARTS && DECORATION.matches(line)

    /** The lines of [text] that have any text, as plain text. */
    fun lines(text: String): Sequence<String> =
        text.lineSequence().map(::line).filter { it.isNotEmpty() }

    /** Inline syntax only: emphasis, strike, code, links, images and escapes. */
    private fun inline(text: String): String {
        if (text.none { it in INLINE_MARKS }) return text
        var out = ESCAPE.replace(text) {
            (HIDDEN_BASE + it.groupValues[1][0].code).toChar().toString()
        }
        out = IMAGE.replace(out, "$1")
        out = LINK.replace(out, "$1")
        out = REFERENCE_LINK.replace(out, "$1")
        out = AUTOLINK.replace(out, "$1")
        out = CODE.replace(out, "$2")
        out = BOLD.replace(out, "$2")
        out = STRIKE.replace(out, "$1")
        out = ITALIC_STAR.replace(out, "$1")
        out = ITALIC_UNDERSCORE.replace(out, "$1")
        out = STRAY_MARKS.replace(out, "")
        if (out.contains('|')) out = TABLE_PIPE.replace(out, " ")
        return buildString(out.length) {
            for (char in out) {
                append(
                    if (char.code in
                        HIDDEN_BASE..HIDDEN_BASE + HIDDEN_SPAN
                    ) {
                        (char.code - HIDDEN_BASE).toChar()
                    } else {
                        char
                    }
                )
            }
        }
    }
}
