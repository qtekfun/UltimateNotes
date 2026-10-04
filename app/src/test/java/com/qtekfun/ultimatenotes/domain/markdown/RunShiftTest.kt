// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

import com.qtekfun.ultimatenotes.domain.TextRange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class RunShiftTest {
    private val bold = StyleRole.Bold

    private fun run(start: Int, end: Int) = StyleRun(TextRange(start, end), bold)

    private fun shifted(before: String, after: String, vararg runs: StyleRun) =
        shiftRuns(runs.toList(), before, after).map { it.range.start to it.range.end }

    @Test
    fun `unchanged text keeps the runs`() {
        val runs = listOf(run(0, 3))
        assertSame(runs, shiftRuns(runs, "abc", "abc"))
    }

    @Test
    fun `typing before a run moves it`() {
        assertEquals(listOf(5 to 7), shifted("ab cd", "abXX cd", run(3, 5)))
    }

    @Test
    fun `typing after a run leaves it`() {
        assertEquals(listOf(0 to 2), shifted("ab cd", "ab cdXX", run(0, 2)))
    }

    @Test
    fun `typing inside a run grows it`() {
        assertEquals(listOf(0 to 6), shifted("abcd", "abXXcd", run(0, 4)))
    }

    @Test
    fun `deleting inside a run shrinks it`() {
        assertEquals(listOf(0 to 2), shifted("abcd", "ad", run(0, 4)))
    }

    @Test
    fun `a run that is deleted entirely disappears`() {
        assertEquals(emptyList<Pair<Int, Int>>(), shifted("ab cd", "ab ", run(3, 5)))
        assertEquals(emptyList<Pair<Int, Int>>(), shifted("abcd", "ad", run(1, 3)))
    }

    @Test
    fun `a run that straddles the edit is cut at it`() {
        assertEquals(listOf(1 to 3), shifted("abcdef", "aXef", run(1, 5)))
    }
}
