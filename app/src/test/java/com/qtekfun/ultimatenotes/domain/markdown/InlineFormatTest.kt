// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

import com.qtekfun.ultimatenotes.domain.TextRange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class InlineFormatTest {
    @Test
    fun `wraps the selection and keeps it selected`() {
        assertEquals("a **‹bold›** b", inline("a ‹bold› b", InlineStyle.Bold))
        assertEquals("a *‹it›* b", inline("a ‹it› b", InlineStyle.Italic))
        assertEquals("a ~~‹gone›~~ b", inline("a ‹gone› b", InlineStyle.Strike))
        assertEquals("a `‹code›` b", inline("a ‹code› b", InlineStyle.Code))
    }

    @Test
    fun `toggling twice is the identity`() {
        for (style in InlineStyle.entries) {
            val once = inline("x ‹word› y", style)
            assertEquals("x ‹word› y", inline(once, style), style.name)
        }
    }

    @Test
    fun `removes markers that sit around the selection`() {
        assertEquals("a ‹bold› b", inline("a **‹bold›** b", InlineStyle.Bold))
        assertEquals("a ‹it› b", inline("a *‹it›* b", InlineStyle.Italic))
    }

    @Test
    fun `removes markers that are part of the selection`() {
        assertEquals("a ‹bold› b", inline("a ‹**bold**› b", InlineStyle.Bold))
        assertEquals("a ‹it› b", inline("a ‹*it*› b", InlineStyle.Italic))
        assertEquals("a ‹x› b", inline("a ‹`x`› b", InlineStyle.Code))
    }

    @Test
    fun `bold and italic do not mistake each other`() {
        assertEquals("***‹x›***", inline("**‹x›**", InlineStyle.Italic))
        assertEquals("**‹x›**", inline("***‹x›***", InlineStyle.Italic))
        assertEquals("*‹x›*", inline("***‹x›***", InlineStyle.Bold))
        assertEquals("***‹x›***", inline("*‹x›*", InlineStyle.Bold))
        assertEquals("‹**x**›", inline("‹***x***›", InlineStyle.Italic))
    }

    @Test
    fun `whitespace around the selection stays outside the markers`() {
        assertEquals(" **‹a b›** ", inline("‹ a b ›", InlineStyle.Bold))
    }

    @Test
    fun `a selection of only whitespace changes nothing`() {
        val text = "a    b"
        val result = toggleInline(text, TextRange(1, 4), InlineStyle.Bold)
        assertEquals(EditResult(text, TextRange(1, 4)), result)
    }

    @Test
    fun `markers alone are not content`() {
        assertEquals("**‹**›**", inline("‹**›", InlineStyle.Bold))
        assertEquals("**‹****›**", inline("‹****›", InlineStyle.Bold))
    }

    @Test
    fun `wraps every line of a multi-line selection separately`() {
        assertEquals("**‹one**\n**two›**", inline("‹one\ntwo›", InlineStyle.Bold))
        assertEquals("**‹one**\n\n**two›**", inline("‹one\n\ntwo›", InlineStyle.Bold))
    }

    @Test
    fun `unwraps a multi-line selection when every line has the style`() {
        assertEquals("‹one\ntwo›", inline("‹**one**\n**two**›", InlineStyle.Bold))
    }

    @Test
    fun `a mixed selection wraps only the lines without the style`() {
        assertEquals("‹**one**\n**two›**", inline("‹**one**\ntwo›", InlineStyle.Bold))
    }

    @Test
    fun `a selection that ends at a line start leaves that line alone`() {
        assertEquals("**‹one›**\ntwo", inline("‹one\n›two", InlineStyle.Bold))
    }

    @Test
    fun `caret inserts an empty pair with the caret inside`() {
        assertEquals("a **|** b", inline("a | b", InlineStyle.Bold))
        assertEquals("`|`", inline("|", InlineStyle.Code))
    }

    @Test
    fun `caret inside an empty pair removes it`() {
        assertEquals("a | b", inline("a **|** b", InlineStyle.Bold))
        assertEquals("a | b", inline("a *|* b", InlineStyle.Italic))
    }

    @Test
    fun `caret between markers of another style inserts a pair`() {
        assertEquals("***|***", inline("**|**", InlineStyle.Italic))
        assertEquals("*|*", inline("***|***", InlineStyle.Bold))
    }

    @Test
    fun `the text between the markers is never touched`() {
        val m = marked("héllo ‹wörld 😀› end")
        for (style in InlineStyle.entries) {
            val wrapped = toggleInline(m.text, m.selection, style)
            assertEquals(
                "wörld 😀",
                wrapped.text.substring(wrapped.selection.start, wrapped.selection.end)
            )
        }
    }

    @Test
    fun `rejects a selection outside the text`() {
        assertThrows(IllegalArgumentException::class.java) {
            toggleInline("abc", TextRange(0, 4), InlineStyle.Bold)
        }
    }

    @Test
    fun `link wraps the selection and puts the caret in the target`() {
        val m = marked("see ‹docs› now")
        assertEquals("see [docs](|) now", insertLink(m.text, m.selection).render())
    }

    @Test
    fun `link of a url keeps the url as target and the caret in the label`() {
        val m = marked("see ‹https://example.com/a?b=1› now")
        assertEquals(
            "see [|](https://example.com/a?b=1) now",
            insertLink(m.text, m.selection).render()
        )
    }

    @Test
    fun `link at a caret inserts an empty one`() {
        val m = marked("see | now")
        assertEquals("see [|]() now", insertLink(m.text, m.selection).render())
    }

    @Test
    fun `link rejects a selection outside the text`() {
        assertThrows(IllegalArgumentException::class.java) { insertLink("abc", TextRange(2, 9)) }
    }
}
