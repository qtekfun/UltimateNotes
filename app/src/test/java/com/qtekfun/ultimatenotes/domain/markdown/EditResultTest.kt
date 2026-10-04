// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

import com.qtekfun.ultimatenotes.domain.TextRange
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EditResultTest {
    private fun edit(original: String, result: String) =
        EditResult(result, TextRange(0, 0)).toEdit(original)

    @Test
    fun `an insertion is only the inserted text`() {
        assertEquals(TextRange(2, 2), edit("abcd", "abXYcd").range)
        assertEquals("XY", edit("abcd", "abXYcd").replacement)
    }

    @Test
    fun `a deletion replaces with nothing`() {
        val e = edit("abXYcd", "abcd")
        assertEquals(TextRange(2, 4), e.range)
        assertEquals("", e.replacement)
    }

    @Test
    fun `identical texts give an empty edit`() {
        val e = edit("same", "same")
        assertEquals(TextRange(4, 4), e.range)
        assertEquals("", e.replacement)
    }

    @Test
    fun `repeated characters do not overlap prefix and suffix`() {
        assertEquals("aaaaa", edit("aaaa", "aaaaa").applyTo("aaaa"))
        assertEquals("aa", edit("aaaa", "aa").applyTo("aaaa"))
    }

    @Test
    fun `surrogate pairs are never split`() {
        val before = "x😀y"
        val after = "x😁y"
        val e = edit(before, after)
        assertEquals(TextRange(1, 3), e.range)
        assertEquals("😁", e.replacement)
        val trailing = edit("😀", "🈀")
        assertEquals(TextRange(0, 2), trailing.range)
    }

    @Test
    fun `the selection travels with the edit`() {
        val e = EditResult("ab!", TextRange(3, 3)).toEdit("ab")
        assertEquals(TextRange(3, 3), e.selection)
    }

    @Test
    fun `applying the edit always gives the new text`() {
        val random = Random(7)
        val alphabet = "ab*\n 😀"
        repeat(500) {
            val a = String(CharArray(random.nextInt(12)) { alphabet.random(random) })
            val b = String(CharArray(random.nextInt(12)) { alphabet.random(random) })
            assertEquals(b, edit(a, b).applyTo(a))
        }
    }
}
