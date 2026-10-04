// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FormatActionTest {
    private fun apply(source: String, action: FormatAction): String {
        val m = marked(source)
        return applyFormat(m.text, m.selection, action).render()
    }

    @Test
    fun `each action reaches its operation`() {
        assertEquals("**‹a›**", apply("‹a›", FormatAction.Inline(InlineStyle.Bold)))
        assertEquals("> a|", apply("a|", FormatAction.Block(BlockKind.Quote)))
        assertEquals("[a](|)", apply("‹a›", FormatAction.Link))
    }

    @Test
    fun `the heading button cycles through three levels and back to plain`() {
        val step1 = apply("T|", FormatAction.Heading)
        val step2 = apply(step1, FormatAction.Heading)
        val step3 = apply(step2, FormatAction.Heading)
        val step4 = apply(step3, FormatAction.Heading)
        assertEquals(listOf("# T|", "## T|", "### T|", "T|"), listOf(step1, step2, step3, step4))
    }

    @Test
    fun `a deeper heading is removed by the button`() {
        assertEquals("T|", apply("#### T|", FormatAction.Heading))
    }

    @Test
    fun `the heading button replaces a list marker`() {
        assertEquals("# T|", apply("- T|", FormatAction.Heading))
    }
}
