// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.export

import com.qtekfun.ultimatenotes.domain.TextRange
import com.qtekfun.ultimatenotes.domain.markdown.InlineStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExportDocumentBuilderTest {
    private fun build(content: String, title: String = "") =
        ExportDocumentBuilder.build(title, content)

    private fun body(content: String) = build(content, title = "\u0000").drop(1)

    private fun styled(block: ExportBlock) =
        block.spans.map { block.text.substring(it.range.start, it.range.end) to it.style }

    @Test
    fun `an empty or blank note has no blocks`() {
        assertEquals(emptyList<ExportBlock>(), build(""))
        assertEquals(emptyList<ExportBlock>(), build("  \n\n \r\n"))
    }

    @Test
    fun `an empty note with a title is just the title`() {
        assertEquals(
            listOf(ExportBlock(ExportBlockKind.Title, "Only title")),
            build("", "Only title")
        )
    }

    @Test
    fun `the first line becomes the title when it matches`() {
        val blocks = build("# Groceries\nmilk", "Groceries")
        assertEquals(listOf(ExportBlockKind.Title, ExportBlockKind.Body), blocks.map { it.kind })
        assertEquals(listOf("Groceries", "milk"), blocks.map { it.text })
    }

    @Test
    fun `the title is derived from the first line when none is given`() {
        val blocks = build("Plain first line\nsecond")
        assertEquals(ExportBlockKind.Title, blocks[0].kind)
        assertEquals("Plain first line", blocks[0].text)
    }

    @Test
    fun `a different title is added in front and nothing is dropped`() {
        val blocks = build("first\nsecond", "Custom title")
        assertEquals(listOf("Custom title", "first", "second"), blocks.map { it.text })
        assertEquals(ExportBlockKind.Title, blocks[0].kind)
    }

    @Test
    fun `a list item is never swallowed as the title`() {
        val blocks = build("- item", "item")
        assertEquals(listOf("item", "item"), blocks.map { it.text })
        assertEquals(ExportBlockKind.Title, blocks[0].kind)
        assertEquals(BULLET, blocks[1].marker)
    }

    @Test
    fun `leading and trailing blank lines are dropped but inner ones kept`() {
        val blocks = build("\n\nfirst\n\nsecond\n\n\n", "t")
        assertEquals(listOf("t", "first", "", "second"), blocks.map { it.text })
    }

    @Test
    fun `CRLF and CR line endings give the same blocks as LF`() {
        val lf = build("# H\nline **b**\n- item\n> quote")
        assertEquals(lf, build("# H\r\nline **b**\r\n- item\r\n> quote"))
        assertEquals(lf, build("# H\rline **b**\r- item\r> quote"))
    }

    @Test
    fun `headings drop their hashes and keep their level`() {
        val blocks = body("## Two ##\n###### Six\n####### seven")
        assertEquals(listOf("Two", "Six", "####### seven"), blocks.map { it.text })
        assertEquals(
            listOf(ExportBlockKind.Heading, ExportBlockKind.Heading, ExportBlockKind.Body),
            blocks.map {
                it.kind
            }
        )
        assertEquals(listOf(2, 6, 0), blocks.map { it.level })
    }

    @Test
    fun `setext headings show their text and not the underline`() {
        val blocks = body("Title text\n==========\nafter")
        assertEquals(listOf("Title text", "after"), blocks.map { it.text })
        assertEquals(ExportBlockKind.Heading, blocks[0].kind)
        assertEquals(1, blocks[0].level)
    }

    @Test
    fun `a multi line setext heading keeps both lines`() {
        assertEquals(listOf("one", "two"), body("one\ntwo\n---").map { it.text })
    }

    @Test
    fun `inline styles are removed from the text and become spans`() {
        val block = body("a **bold** and *it* and ~~gone~~ and `code` end").single()
        assertEquals("a bold and it and gone and code end", block.text)
        assertEquals(
            listOf(
                "bold" to InlineStyle.Bold,
                "it" to InlineStyle.Italic,
                "gone" to InlineStyle.Strike,
                "code" to InlineStyle.Code
            ),
            styled(block)
        )
    }

    @Test
    fun `underscore emphasis and triple markers are handled`() {
        val block = body("__bold__ _it_ ***both***").single()
        assertEquals("bold it both", block.text)
        assertTrue(
            block.spans.any {
                it.style == InlineStyle.Bold &&
                    block.text.substring(it.range.start, it.range.end) == "both"
            }
        )
        assertTrue(
            block.spans.any {
                it.style == InlineStyle.Italic &&
                    block.text.substring(it.range.start, it.range.end) == "both"
            }
        )
    }

    @Test
    fun `a double backtick code span loses all its backticks`() {
        val block = body("x ``a ` b`` y").single()
        assertEquals("x a ` b y", block.text)
        assertTrue(block.spans.single().style == InlineStyle.Code)
    }

    @Test
    fun `styles inside headings and lists are kept`() {
        val heading = body("# A **b**").single()
        assertEquals("A b", heading.text)
        assertEquals(listOf("b" to InlineStyle.Bold), styled(heading))
        val item = body("- *x*").single()
        assertEquals("x", item.text)
        assertEquals(listOf("x" to InlineStyle.Italic), styled(item))
    }

    @Test
    fun `emphasis that spans lines is styled on each line without stray markers`() {
        val blocks = body("**one\ntwo**")
        assertEquals(listOf("one", "two"), blocks.map { it.text })
        assertTrue(blocks.all { it.spans.size == 1 && it.spans.single().style == InlineStyle.Bold })
    }

    @Test
    fun `links show their text only`() {
        assertEquals("see docs now", body("see [docs](https://example.com/a_b) now").single().text)
    }

    @Test
    fun `an autolink is left as written`() {
        assertEquals("<https://example.com>", body("<https://example.com>").single().text)
    }

    @Test
    fun `bullets numbers and checklists get markers`() {
        val blocks = body("- a\n* b\n+ c\n1. one\n7) seven\n- [ ] todo\n- [x] done\n- [X] done too")
        assertEquals(
            listOf(BULLET, BULLET, BULLET, "1.", "7.", UNCHECKED, CHECKED, CHECKED),
            blocks.map { it.marker }
        )
        assertEquals(
            listOf("a", "b", "c", "one", "seven", "todo", "done", "done too"),
            blocks.map {
                it.text
            }
        )
    }

    @Test
    fun `nested items are indented by depth and continuation lines have an empty marker`() {
        // The last line lazily continues the paragraph of "c", so it sits at c's depth.
        val blocks = body("- a\n  - b\n    - c\n  more of c")
        assertEquals(listOf("a", "b", "c", "more of c"), blocks.map { it.text })
        assertEquals(listOf(0, 1, 2, 2), blocks.map { it.indent })
        assertEquals(listOf(BULLET, BULLET, BULLET, ""), blocks.map { it.marker })
    }

    @Test
    fun `plain text has no marker and no indent`() {
        val block = body("hello").single()
        assertNull(block.marker)
        assertEquals(0, block.indent)
        assertEquals(0, block.quoteDepth)
    }

    @Test
    fun `quotes are counted and their prefix removed`() {
        val blocks = body("> one\n> > two\n> lazy")
        assertEquals(listOf("one", "two", "lazy"), blocks.map { it.text })
        // A lazy continuation line belongs to the innermost quote that is still open.
        assertEquals(listOf(1, 2, 2), blocks.map { it.quoteDepth })
    }

    @Test
    fun `a heading or list inside a quote loses both prefixes`() {
        val blocks = body("> # Head\n> - item")
        assertEquals(listOf("Head", "item"), blocks.map { it.text })
        assertEquals(ExportBlockKind.Heading, blocks[0].kind)
        assertEquals(BULLET, blocks[1].marker)
    }

    @Test
    fun `unsupported syntax is printed as written`() {
        val source = "```kotlin\nval x = 1\n\n  indented\n```\n" +
            "| a | b |\n|---|---|\n| 1 | 2 |\n<div>html</div>\n---"
        val blocks = body(source)
        assertEquals(source.lines(), blocks.map { it.text })
        assertTrue(
            blocks.all {
                it.kind == ExportBlockKind.Body && it.marker == null &&
                    it.spans.isEmpty()
            }
        )
    }

    @Test
    fun `long words and unicode survive untouched`() {
        val word = "x".repeat(5_000)
        val text = "emoji 😀 é́ 中文 $word"
        assertEquals(text, body(text).single().text)
    }

    @Test
    fun `an emoji inside styled text keeps the spans on the right characters`() {
        val block = body("😀 **b😀**").single()
        assertEquals("😀 b😀", block.text)
        assertEquals(TextRange(3, 6), block.spans.single().range)
    }

    @Test
    fun `a huge note builds one block per line`() {
        val content = (1..20_000).joinToString("\n") { "- item $it **x**" }
        val blocks = body(content)
        assertEquals(20_000, blocks.size)
        assertEquals("item 20000 x", blocks.last().text)
    }

    @Test
    fun `malformed markers never crash`() {
        val inputs =
            listOf(
                "**", "****", "`", "``", "[", "](", "[](", "- ", "> ", "# ", "#", "~~",
                "*a", "a*", "\\*x\\*", "- [ ]", "1."
            )
        inputs.forEach {
            build(it, "t")
            build("x\n$it\n$it")
        }
    }

    private companion object {
        const val BULLET = ExportDocumentBuilder.BULLET
        const val CHECKED = ExportDocumentBuilder.CHECKED
        const val UNCHECKED = ExportDocumentBuilder.UNCHECKED
    }
}
