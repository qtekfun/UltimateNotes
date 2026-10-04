// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.export

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Needs a device or emulator: renders real PDFs and reads them back with [PdfRenderer]. */
@RunWith(AndroidJUnit4::class)
class PdfExporterTest {
    private val exporter = PdfExporter()

    private fun pageCount(bytes: ByteArray): Int {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File.createTempFile("export", ".pdf", context.cacheDir)
        try {
            file.writeBytes(bytes)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { return it.pageCount }
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun emptyNoteIsOneBlankPage() {
        val bytes = exporter.render("", "")
        assertTrue(String(bytes, 0, 5, Charsets.US_ASCII) == "%PDF-")
        assertEquals(1, pageCount(bytes))
    }

    @Test
    fun everyKindOfBlockRendersWithoutCrashing() {
        val content = """
            # Heading
            Some **bold**, *italic*, ~~strike~~ and `code` with an emoji 😀.
            - bullet
            1. numbered
            - [ ] todo
            - [x] done
            > quoted
            ```
            raw
            ```
        """.trimIndent()
        assertEquals(1, pageCount(exporter.render("Heading", content)))
    }

    @Test
    fun aLongNoteIsPaginated() {
        val content = (1..400).joinToString("\n") { "Line $it of a long note" }
        assertTrue(pageCount(exporter.render("Long", content)) > 1)
    }

    @Test
    fun aWordWiderThanThePageIsWrapped() {
        val content = "x".repeat(5_000)
        assertTrue(pageCount(exporter.render("Wide", content)) >= 1)
    }
}
