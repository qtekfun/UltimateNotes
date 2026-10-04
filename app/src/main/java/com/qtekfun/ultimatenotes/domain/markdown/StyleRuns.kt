// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

import com.qtekfun.ultimatenotes.domain.TextRange

/** How a region of the source is drawn in the editor. The UI maps roles to concrete text styles. */
sealed interface StyleRole {
    data class Heading(val level: Int) : StyleRole

    data object Bold : StyleRole

    data object Italic : StyleRole

    data object Strike : StyleRole

    data object Code : StyleRole

    data object Link : StyleRole

    /** The text of a quote line. */
    data object Quote : StyleRole

    /** Syntax characters (`**`, `#`, `>`, list markers, link targets): shown, but quiet. */
    data object Marker : StyleRole

    /** The text of a ticked checklist line. */
    data object Done : StyleRole

    /** The `[ ]` / `[x]` of a checklist line, drawn as a box glyph by the editor. */
    data class Checkbox(val checked: Boolean) : StyleRole
}

data class StyleRun(val range: TextRange, val role: StyleRole)

/**
 * Turns analyzer segments into the style runs the editor draws. Runs may overlap; the UI applies
 * them in order, so later ones win (markers come after the text they sit in). Ranges are clamped
 * to [text], which lets a slightly stale analysis still be applied safely.
 */
object StyleRuns {
    private val LIST_MARKER = Regex("""[-*+]|\d{1,9}[.)]""")
    private val QUOTE_MARKER = Regex("""^[ \t]*(?:>[ \t]?)+""")

    fun of(text: String, segments: List<Segment>): List<StyleRun> {
        val runs = mutableListOf<StyleRun>()
        for (segment in segments) collect(text, segment, runs)
        return runs.mapNotNull { clamp(it, text.length) }
    }

    private fun clamp(run: StyleRun, length: Int): StyleRun? {
        val start = run.range.start.coerceAtMost(length)
        val end = run.range.end.coerceAtMost(length)
        return if (start < end) StyleRun(TextRange(start, end), run.role) else null
    }

    private fun collect(text: String, segment: Segment, out: MutableList<StyleRun>) {
        val range = segment.range
        if (range.start >= text.length) return
        when (val kind = segment.kind) {
            is SegmentKind.Heading -> heading(text, range, kind.level, out)

            SegmentKind.Quote -> quote(text, range, out)

            is SegmentKind.ListItem -> listMarker(text, range.start, out)

            is SegmentKind.Checklist -> checklist(text, range, kind, out)

            SegmentKind.Bold -> inline(range, StyleRole.Bold, BOLD_MARKER, out)

            SegmentKind.Italic -> inline(range, StyleRole.Italic, 1, out)

            SegmentKind.Strike -> inline(
                range,
                StyleRole.Strike,
                markerRun(text, range, '~'),
                out
            )

            SegmentKind.Code -> inline(
                range,
                StyleRole.Code,
                markerRun(text, range, '`'),
                out
            )

            is SegmentKind.Link -> link(text, range, out)

            SegmentKind.Opaque -> Unit
        }
        for (child in segment.children) collect(text, child, out)
    }

    private fun heading(text: String, range: TextRange, level: Int, out: MutableList<StyleRun>) {
        out += StyleRun(range, StyleRole.Heading(level))
        var end = range.start
        while (end < range.end && text[end] == '#') end++
        if (end > range.start) out += StyleRun(TextRange(range.start, end), StyleRole.Marker)
    }

    private fun quote(text: String, range: TextRange, out: MutableList<StyleRun>) {
        var line = lineAt(text, range.start)
        while (true) {
            val start = maxOf(line.start, range.start)
            val end = minOf(line.end, range.end)
            if (start < end) {
                out += StyleRun(TextRange(start, end), StyleRole.Quote)
                QUOTE_MARKER.find(text.substring(start, end))?.let {
                    out += StyleRun(TextRange(start, start + it.value.length), StyleRole.Marker)
                }
            }
            if (line.next >= range.end || line.next == line.start) return
            line = lineAt(text, line.next)
        }
    }

    private fun listMarker(text: String, start: Int, out: MutableList<StyleRun>) {
        LIST_MARKER.matchAt(text, start)?.let {
            out += StyleRun(TextRange(start, start + it.value.length), StyleRole.Marker)
        }
    }

    private fun checklist(
        text: String,
        range: TextRange,
        kind: SegmentKind.Checklist,
        out: MutableList<StyleRun>
    ) {
        val box = kind.box
        if (kind.checked) {
            val lineEnd = lineAt(text, box.end.coerceAtMost(text.length)).end
            out += StyleRun(TextRange(box.end, maxOf(box.end, lineEnd)), StyleRole.Done)
        }
        listMarker(text, range.start, out)
        out += StyleRun(box, StyleRole.Checkbox(kind.checked))
    }

    private fun inline(range: TextRange, role: StyleRole, marker: Int, out: MutableList<StyleRun>) {
        out += StyleRun(range, role)
        if (marker > 0 && range.end - range.start > 2 * marker) {
            out += StyleRun(TextRange(range.start, range.start + marker), StyleRole.Marker)
            out += StyleRun(TextRange(range.end - marker, range.end), StyleRole.Marker)
        }
    }

    private fun markerRun(text: String, range: TextRange, char: Char): Int {
        var n = 0
        while (range.start + n < range.end && text[range.start + n] == char) n++
        return if (n * 2 < range.end - range.start) n else 0
    }

    private fun link(text: String, range: TextRange, out: MutableList<StyleRun>) {
        out += StyleRun(range, StyleRole.Link)
        val target = text.lastIndexOf("](", range.end - 1)
        if (text.getOrNull(range.start) == '[' && target > range.start && target < range.end) {
            out += StyleRun(TextRange(range.start, range.start + 1), StyleRole.Marker)
            out += StyleRun(TextRange(target, range.end), StyleRole.Marker)
        }
    }

    private const val BOLD_MARKER = 2
}
