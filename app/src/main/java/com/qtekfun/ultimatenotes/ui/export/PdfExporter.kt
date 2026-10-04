// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import androidx.core.graphics.withTranslation
import com.qtekfun.ultimatenotes.domain.export.ExportBlock
import com.qtekfun.ultimatenotes.domain.export.ExportBlockKind
import com.qtekfun.ultimatenotes.domain.export.ExportDocumentBuilder
import com.qtekfun.ultimatenotes.domain.export.MeasuredBlock
import com.qtekfun.ultimatenotes.domain.export.PdfPaginator
import com.qtekfun.ultimatenotes.domain.export.Placement
import com.qtekfun.ultimatenotes.domain.markdown.InlineStyle
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Draws a note as an A4 PDF with [PdfDocument] only. Wrapping uses [StaticLayout]; what goes on
 * which page is decided by the pure [PdfPaginator]. Thin on purpose: the logic is in `domain`.
 */
class PdfExporter @Inject constructor() {
    /** One block, laid out at its final width. */
    private class Laid(
        val block: ExportBlock,
        val layout: StaticLayout,
        val paint: TextPaint,
        val left: Float,
        val measured: MeasuredBlock
    )

    private val barPaint = Paint().apply {
        color = QUOTE_COLOR
        strokeWidth = QUOTE_BAR_WIDTH
    }

    fun render(title: String, content: String): ByteArray {
        val laid = ExportDocumentBuilder.build(title, content).map(::lay)
        val pages = PdfPaginator.paginate(laid.map { it.measured }, CONTENT_HEIGHT)
        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, planned ->
                val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
                val page = document.startPage(info)
                planned.placements.forEach { draw(page.canvas, laid[it.block], it) }
                document.finishPage(page)
            }
            return ByteArrayOutputStream().also { document.writeTo(it) }.toByteArray()
        } finally {
            document.close()
        }
    }

    private fun lay(block: ExportBlock): Laid {
        val paint = paintFor(block)
        val left = block.indent * INDENT + block.quoteDepth * QUOTE_STEP +
            if (block.marker != null) MARKER_WIDTH else 0f
        val width = (CONTENT_WIDTH - left).toInt().coerceAtLeast(MIN_TEXT_WIDTH)
        val text = SpannableString(block.text)
        block.spans.forEach { span ->
            val what: Any = when (span.style) {
                InlineStyle.Bold -> StyleSpan(Typeface.BOLD)
                InlineStyle.Italic -> StyleSpan(Typeface.ITALIC)
                InlineStyle.Strike -> StrikethroughSpan()
                InlineStyle.Code -> TypefaceSpan("monospace")
            }
            text.setSpan(what, span.range.start, span.range.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .setLineSpacing(0f, LINE_SPACING)
            .build()
        val heights =
            List(layout.lineCount) { (layout.getLineBottom(it) - layout.getLineTop(it)).toFloat() }
        val measured = MeasuredBlock(
            lineHeights = heights,
            spaceBefore = spaceBefore(block, paint.textSize),
            spaceAfter = if (block.kind == ExportBlockKind.Title) TITLE_SPACE_AFTER else 0f,
            keepWithNext = block.kind != ExportBlockKind.Body
        )
        return Laid(block, layout, paint, left, measured)
    }

    private fun spaceBefore(block: ExportBlock, size: Float): Float = when {
        block.kind == ExportBlockKind.Heading -> size * HEADING_SPACE
        block.marker != null -> LIST_SPACE
        else -> 0f
    }

    private fun paintFor(block: ExportBlock): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = when (block.kind) {
            ExportBlockKind.Title -> TITLE_SIZE

            ExportBlockKind.Heading -> HEADING_SIZES[
                (block.level - 1).coerceIn(
                    0,
                    HEADING_SIZES.lastIndex
                )
            ]

            ExportBlockKind.Body -> BODY_SIZE
        }
        typeface =
            if (block.kind == ExportBlockKind.Body) Typeface.DEFAULT else Typeface.DEFAULT_BOLD
    }

    private fun draw(canvas: Canvas, laid: Laid, placement: Placement) {
        val layout = laid.layout
        val first = placement.firstLine
        val last = first + placement.lineCount - 1
        val top = layout.getLineTop(first).toFloat()
        val bottom = layout.getLineBottom(last).toFloat()
        val x = MARGIN + laid.left
        val y = MARGIN + placement.y
        canvas.withTranslation(x, y - top) {
            clipRect(0f, top, layout.width.toFloat(), bottom)
            layout.draw(this)
        }
        val block = laid.block
        val markerX = MARGIN + block.indent * INDENT + block.quoteDepth * QUOTE_STEP
        if (first == 0 && !block.marker.isNullOrEmpty()) {
            val baseline = y + layout.getLineBaseline(0) - top
            canvas.drawText(block.marker, markerX, baseline, laid.paint)
        }
        repeat(block.quoteDepth) {
            val barX = MARGIN + block.indent * INDENT + it * QUOTE_STEP + QUOTE_BAR_WIDTH
            canvas.drawLine(barX, y, barX, y + bottom - top, barPaint)
        }
    }

    companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        private const val MARGIN = 56f
        private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
        private const val CONTENT_HEIGHT = PAGE_HEIGHT - 2 * MARGIN
        private const val MIN_TEXT_WIDTH = 40
        private const val INDENT = 18f
        private const val MARKER_WIDTH = 18f
        private const val QUOTE_STEP = 12f
        private const val QUOTE_BAR_WIDTH = 2f
        private const val QUOTE_COLOR = 0xFF9E9E9E.toInt()
        private const val BODY_SIZE = 11f
        private const val TITLE_SIZE = 24f
        private val HEADING_SIZES = floatArrayOf(20f, 17f, 14.5f, 13f, 12f, 11f)
        private const val LINE_SPACING = 1.15f
        private const val HEADING_SPACE = 0.7f
        private const val LIST_SPACE = 2f
        private const val TITLE_SPACE_AFTER = 10f
    }
}
