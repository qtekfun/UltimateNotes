// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.export

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PdfPaginatorTest {
    private fun block(
        lines: Int,
        height: Float = 10f,
        before: Float = 0f,
        after: Float = 0f,
        keep: Boolean = false
    ) = MeasuredBlock(List(lines) { height }, before, after, keep)

    private fun shape(pages: List<PlannedPage>) =
        pages.map { page -> page.placements.map { Triple(it.block, it.firstLine, it.lineCount) } }

    @Test
    fun `no blocks still make one empty page`() {
        val pages = PdfPaginator.paginate(emptyList(), 100f)
        assertEquals(1, pages.size)
        assertTrue(pages.single().placements.isEmpty())
    }

    @Test
    fun `a block without lines places nothing`() {
        val pages = PdfPaginator.paginate(listOf(block(0), block(2)), 100f)
        assertEquals(listOf(listOf(Triple(1, 0, 2))), shape(pages))
    }

    @Test
    fun `blocks that fit share a page and are stacked with their spacing`() {
        val pages = PdfPaginator.paginate(listOf(block(2, after = 5f), block(1, before = 3f)), 100f)
        assertEquals(1, pages.size)
        assertEquals(listOf(0f, 28f), pages.single().placements.map { it.y })
    }

    @Test
    fun `space before is dropped at the top of a page`() {
        val pages = PdfPaginator.paginate(listOf(block(1, before = 7f)), 100f)
        assertEquals(0f, pages.single().placements.single().y)
    }

    @Test
    fun `a block exactly filling the page stays on it`() {
        val pages = PdfPaginator.paginate(listOf(block(10)), 100f)
        assertEquals(listOf(listOf(Triple(0, 0, 10))), shape(pages))
    }

    @Test
    fun `a long block is split by lines across pages`() {
        val pages = PdfPaginator.paginate(listOf(block(25)), 100f)
        assertEquals(
            listOf(
                listOf(Triple(0, 0, 10)),
                listOf(Triple(0, 10, 10)),
                listOf(Triple(0, 20, 5))
            ),
            shape(pages)
        )
        assertTrue(pages.all { it.placements.single().y == 0f })
    }

    @Test
    fun `a split never leaves a single line at the bottom of a page`() {
        // 9 lines used, one line of room left, and a 5-line block: it starts on the next page.
        val pages = PdfPaginator.paginate(listOf(block(9), block(5)), 100f)
        assertEquals(listOf(listOf(Triple(0, 0, 9)), listOf(Triple(1, 0, 5))), shape(pages))
    }

    @Test
    fun `a split never leaves a single line at the top of the next page`() {
        // 4 lines of room for a 5-line block: take 3 so that 2 go on the next page.
        val pages = PdfPaginator.paginate(listOf(block(6), block(5)), 100f)
        assertEquals(
            listOf(listOf(Triple(0, 0, 6), Triple(1, 0, 3)), listOf(Triple(1, 3, 2))),
            shape(pages)
        )
    }

    @Test
    fun `a short block that does not fit moves whole to the next page`() {
        val pages = PdfPaginator.paginate(listOf(block(9), block(2)), 100f)
        assertEquals(listOf(listOf(Triple(0, 0, 9)), listOf(Triple(1, 0, 2))), shape(pages))
    }

    @Test
    fun `a heading is kept with the line after it`() {
        val heading = block(1, keep = true)
        val pages = PdfPaginator.paginate(listOf(block(9), heading, block(3)), 100f)
        assertEquals(
            listOf(listOf(Triple(0, 0, 9)), listOf(Triple(1, 0, 1), Triple(2, 0, 3))),
            shape(pages)
        )
    }

    @Test
    fun `a heading with room for the next line stays`() {
        val pages = PdfPaginator.paginate(listOf(block(7), block(1, keep = true), block(2)), 100f)
        assertEquals(1, pages.size)
    }

    @Test
    fun `a heading is not pushed anywhere when it is last or the next block is empty`() {
        val last = PdfPaginator.paginate(listOf(block(10), block(1, keep = true)), 100f)
        assertEquals(2, last.size)
        val beforeEmpty = PdfPaginator.paginate(
            listOf(block(9), block(1, keep = true), block(0)),
            100f
        )
        assertEquals(1, beforeEmpty.size)
    }

    @Test
    fun `a heading at the top of a page is never pushed again`() {
        val pages = PdfPaginator.paginate(
            listOf(block(1, height = 100f, keep = true), block(2)),
            100f
        )
        assertEquals(listOf(listOf(Triple(0, 0, 1)), listOf(Triple(1, 0, 2))), shape(pages))
    }

    @Test
    fun `a line taller than the page is placed on a page of its own and layout ends`() {
        val tall = MeasuredBlock(listOf(150f, 10f, 10f))
        val pages = PdfPaginator.paginate(listOf(block(1), tall, block(1)), 100f)
        assertEquals(
            listOf(
                listOf(Triple(0, 0, 1)),
                listOf(Triple(1, 0, 1)),
                listOf(Triple(1, 1, 2), Triple(2, 0, 1))
            ),
            shape(pages)
        )
    }

    @Test
    fun `every line is placed exactly once and in order for a huge note`() {
        val blocks =
            List(20_000) {
                block(1 + it % 7, height = 12f, before = (it % 3).toFloat(), after = 2f)
            }
        val pages = PdfPaginator.paginate(blocks, 730f)
        val placed = pages.flatMap { it.placements }
        blocks.forEachIndexed { index, b ->
            val mine = placed.filter { it.block == index }
            assertEquals(b.lineHeights.size, mine.sumOf { it.lineCount })
            assertEquals(
                mine.map {
                    it.firstLine
                },
                mine.runningFold(0) { at, p -> at + p.lineCount }.dropLast(1)
            )
        }
        assertTrue(pages.none { it.placements.isEmpty() })
        pages.forEach { page ->
            val last = page.placements.last()
            val bottom =
                last.y + (last.firstLine until last.firstLine + last.lineCount).sumOf { 12.0 }
            assertTrue(bottom <= 730.01, "overflowing page: $bottom")
        }
    }

    @Test
    fun `page height must be positive`() {
        assertThrows<IllegalArgumentException> { PdfPaginator.paginate(emptyList(), 0f) }
    }
}
