// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

import com.qtekfun.ultimatenotes.domain.TextRange
import kotlin.system.measureNanoTime
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The editor re-analyzes the whole note on every keystroke (ADR 0001, T11 spike), so the cost on a
 * big note matters. The budget here is deliberately loose: it catches an accidental quadratic
 * blow-up, not a slow machine.
 */
class LargeNoteTest {
    private val paragraph = """
        ## Section heading
        Some **bold** and *italic* text with ~~strike~~, `code` and a [link](https://example.com/x).
        - [ ] open task with text
        - [x] done task with text
        > a quoted line that goes on for a while
        1. numbered item
        plain paragraph line without any markup at all, just words to fill the space up
    """.trimIndent() + "\n\n"

    private val note = paragraph.repeat(MIN_BYTES / paragraph.length + 1)

    private fun medianMillis(block: () -> Unit): Double {
        repeat(WARMUP) { block() }
        val samples = List(RUNS) { measureNanoTime(block) / NANOS_PER_MILLI }
        return samples.sorted()[RUNS / 2]
    }

    @Test
    fun `analyzing and styling a 50 KB note is fast enough to do per keystroke`() {
        assertTrue(note.length >= MIN_BYTES)
        val millis = medianMillis { StyleRuns.of(note, MarkdownAnalyzer.analyze(note)) }
        println("T11 spike: analyze+runs of ${note.length} chars: median $millis ms")
        assertTrue(millis < BUDGET_MILLIS, "took $millis ms")
    }

    @Test
    fun `a formatting edit on a 50 KB note is fast`() {
        val middle = note.length / 2
        val millis = medianMillis {
            setBlock(note, TextRange(middle, middle), BlockKind.Quote)
                .toEdit(note)
        }
        println("T11 spike: setBlock+toEdit on ${note.length} chars: median $millis ms")
        assertTrue(millis < BUDGET_MILLIS, "took $millis ms")
    }

    private companion object {
        const val MIN_BYTES = 50_000
        const val WARMUP = 5
        const val RUNS = 15
        const val NANOS_PER_MILLI = 1_000_000.0
        const val BUDGET_MILLIS = 500.0
    }
}
