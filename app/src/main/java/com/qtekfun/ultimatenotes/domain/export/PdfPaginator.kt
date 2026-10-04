// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.export

/**
 * A block already measured by the renderer: the height of each of its wrapped lines, and the
 * space above and below it. [spaceBefore] is dropped at the top of a page. A [keepWithNext]
 * block (a heading) is never left alone at the bottom of a page.
 */
data class MeasuredBlock(
    val lineHeights: List<Float>,
    val spaceBefore: Float = 0f,
    val spaceAfter: Float = 0f,
    val keepWithNext: Boolean = false
)

/** Lines [firstLine, firstLine + lineCount) of block [block], drawn with their top at [y]. */
data class Placement(val block: Int, val firstLine: Int, val lineCount: Int, val y: Float)

data class PlannedPage(val placements: List<Placement>)

/**
 * Decides which lines go on which page. Pure geometry, no drawing: a block is split between pages
 * line by line but never leaves a single line alone on either side (orphan/widow), and a line
 * taller than a whole page is still placed (on a page of its own) so the layout always ends.
 */
object PdfPaginator {
    const val MIN_SPLIT_LINES = 2
    private const val EPSILON = 0.01f

    /** Always at least one page, even for no blocks, as a PDF must have one. */
    fun paginate(blocks: List<MeasuredBlock>, pageHeight: Float): List<PlannedPage> {
        require(pageHeight > 0f) { "Page height must be positive" }
        return Run(blocks, pageHeight).pages()
    }

    private class Run(private val blocks: List<MeasuredBlock>, private val pageHeight: Float) {
        private val done = mutableListOf<PlannedPage>()
        private var page = mutableListOf<Placement>()
        private var cursor = 0f

        fun pages(): List<PlannedPage> {
            blocks.indices.forEach(::layOut)
            done += PlannedPage(page)
            return done
        }

        private fun newPage() {
            done += PlannedPage(page)
            page = mutableListOf()
            cursor = 0f
        }

        private fun layOut(index: Int) {
            val block = blocks[index]
            val heights = block.lineHeights
            var first = 0
            while (first < heights.size) {
                val atTop = page.isEmpty()
                val before = if (atTop || first > 0) 0f else block.spaceBefore
                val room = pageHeight - cursor - before
                val fit = linesFitting(heights, first, room)
                val left = heights.size - first
                val take = if (fit >= left) left else splitPoint(fit, first, left, atTop)
                val height = (first until first + take).sumOf { heights[it].toDouble() }.toFloat()
                if (take == left && !atTop && !fitsWithNext(index, room - height)) {
                    newPage()
                } else if (take == 0) {
                    newPage()
                } else {
                    page += Placement(index, first, take, cursor + before)
                    cursor += before + height
                    first += take
                    if (first == heights.size) cursor += block.spaceAfter
                }
            }
        }

        private fun linesFitting(heights: List<Float>, first: Int, room: Float): Int {
            var used = 0f
            var count = 0
            while (first + count < heights.size &&
                used + heights[first + count] <= room + EPSILON
            ) {
                used += heights[first + count]
                count++
            }
            return count
        }

        /** How many of the [left] lines go on this page when only [fit] fit; 0 means start a new page. */
        private fun splitPoint(fit: Int, first: Int, left: Int, atTop: Boolean): Int {
            var take = fit
            if (left - take < MIN_SPLIT_LINES) take = left - MIN_SPLIT_LINES
            if (first == 0 && take < MIN_SPLIT_LINES) take = 0
            if (take <= 0 && atTop) take = fit.coerceAtLeast(1)
            return take.coerceAtLeast(0)
        }

        /** Whether a [keepWithNext] block's following block can start in the [roomAfter] that is left. */
        private fun fitsWithNext(index: Int, roomAfter: Float): Boolean {
            val block = blocks[index]
            val next = blocks.getOrNull(index + 1)?.takeIf { it.lineHeights.isNotEmpty() }
            if (!block.keepWithNext || next == null) return true
            return roomAfter + EPSILON >=
                block.spaceAfter + next.spaceBefore + next.lineHeights.first()
        }
    }
}
