// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.editor

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import com.qtekfun.ultimatenotes.domain.markdown.MarkdownAnalyzer
import com.qtekfun.ultimatenotes.domain.markdown.StyleRole
import com.qtekfun.ultimatenotes.domain.markdown.StyleRun
import com.qtekfun.ultimatenotes.domain.markdown.StyleRuns
import com.qtekfun.ultimatenotes.domain.markdown.shiftRuns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

/** The theme colors the editor draws Markdown with. */
@Immutable
data class EditorPalette(
    val marker: Color,
    val link: Color,
    val quote: Color,
    val codeBackground: Color,
    val accent: Color,
    val done: Color
)

/**
 * Where the editor's style runs come from. Small notes are analyzed on the spot, so styling is
 * never a keystroke behind. Big ones (above [SYNC_LIMIT] characters; a 50 KB note takes ~10 ms
 * per pass on a desktop JVM, see ADR 0001) are analyzed by [track] on a background thread, and
 * the last result is shifted over the edits made since ([shiftRuns]), which keeps typing smooth.
 */
class StyleRunSource {
    private class Analysis(val text: String, val runs: List<StyleRun>)

    private var latest by mutableStateOf(Analysis("", emptyList()))
    private var memo = Analysis("", emptyList())

    /** The runs to draw [text] with. Reads snapshot state, so a finished analysis redraws. */
    fun runsFor(text: String): List<StyleRun> {
        if (text.length <= SYNC_LIMIT) {
            if (memo.text != text) memo = Analysis(text, analyze(text))
            return memo.runs
        }
        val analysis = latest
        if (memo.text != text) memo = Analysis(text, shiftRuns(analysis.runs, analysis.text, text))
        return memo.runs
    }

    /** Keeps the analysis of big notes current; runs until cancelled. */
    suspend fun track(state: TextFieldState) {
        snapshotFlow { state.text.toString() }.collectLatest { text ->
            if (text.length > SYNC_LIMIT) {
                latest = withContext(Dispatchers.Default) { Analysis(text, analyze(text)) }
                memo = Analysis("", emptyList())
            }
        }
    }

    private fun analyze(text: String) = StyleRuns.of(text, MarkdownAnalyzer.analyze(text))

    companion object {
        const val SYNC_LIMIT = 20_000
    }
}

/**
 * Draws the Markdown source with styles ("live preview"): the text itself is never changed.
 * The `[ ]` / `[x]` of a checklist is shown as a box glyph padded with zero-width spaces to the
 * same length, so every offset in the drawn text is also the offset in the source.
 */
class MarkdownStyler(private val source: StyleRunSource, private val palette: EditorPalette) :
    OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        val runs = source.runsFor(asCharSequence().toString())
        for (run in runs) {
            val role = run.role
            if (role is StyleRole.Checkbox) {
                replace(
                    run.range.start,
                    run.range.end,
                    if (role.checked) CHECKED_GLYPH else UNCHECKED_GLYPH
                )
            }
        }
        for (run in runs) {
            styleOf(run.role, palette)?.let { addStyle(it, run.range.start, run.range.end) }
        }
    }

    private companion object {
        // One glyph plus two zero-width spaces: as long as the three characters `[ ]` it replaces.
        const val UNCHECKED_GLYPH = "☐​​"
        const val CHECKED_GLYPH = "☑​​"
    }
}

private const val H1_SIZE = 1.5f
private const val H2_SIZE = 1.3f
private const val H3_SIZE = 1.15f

@Suppress("CyclomaticComplexMethod") // one branch per role
internal fun styleOf(role: StyleRole, palette: EditorPalette): SpanStyle? = when (role) {
    is StyleRole.Heading -> SpanStyle(
        fontWeight = FontWeight.Bold,
        fontSize = when (role.level) {
            1 -> H1_SIZE.em
            2 -> H2_SIZE.em
            3 -> H3_SIZE.em
            else -> 1.em
        }
    )

    StyleRole.Bold -> SpanStyle(fontWeight = FontWeight.Bold)

    StyleRole.Italic -> SpanStyle(fontStyle = FontStyle.Italic)

    StyleRole.Strike -> SpanStyle(textDecoration = TextDecoration.LineThrough)

    StyleRole.Code -> SpanStyle(
        fontFamily = FontFamily.Monospace,
        background = palette.codeBackground
    )

    StyleRole.Link -> SpanStyle(color = palette.link, textDecoration = TextDecoration.Underline)

    StyleRole.Quote -> SpanStyle(color = palette.quote, fontStyle = FontStyle.Italic)

    StyleRole.Marker -> SpanStyle(color = palette.marker)

    StyleRole.Done -> SpanStyle(color = palette.done, textDecoration = TextDecoration.LineThrough)

    is StyleRole.Checkbox -> SpanStyle(color = palette.accent, fontWeight = FontWeight.Bold)
}
