// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.export

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

class ExportFileNameTest {
    @Test
    fun `a plain title keeps its words and gets the extension`() {
        assertEquals("Shopping list.md", ExportFileName.of("Shopping list", ExportFormat.MARKDOWN))
        assertEquals("Shopping list.pdf", ExportFileName.of("Shopping list", ExportFormat.PDF))
    }

    @Test
    fun `path separators and illegal characters are removed`() {
        assertEquals("a b c d e f g h i", ExportFileName.base("a/b\\c:d*e?f\"g<h>i"))
        assertEquals("etc passwd", ExportFileName.base("../etc/passwd"))
        assertEquals("a b", ExportFileName.base("a|b"))
    }

    @Test
    fun `control characters and whitespace runs collapse`() {
        assertEquals("a b c", ExportFileName.base("a\u0000\n\t b‎  c"))
    }

    @Test
    fun `leading and trailing dots and spaces go`() {
        assertEquals("hidden", ExportFileName.base("  ..hidden.. "))
    }

    @Test
    fun `nothing left falls back to note`() {
        assertEquals("note", ExportFileName.base(""))
        assertEquals("note", ExportFileName.base("   "))
        assertEquals("note", ExportFileName.base("///"))
        assertEquals("note", ExportFileName.base(".."))
        assertEquals("note.md", ExportFileName.of("", ExportFormat.MARKDOWN))
    }

    @Test
    fun `long titles are cut without splitting a surrogate pair`() {
        assertEquals(ExportFileName.MAX_BASE_LENGTH, ExportFileName.base("x".repeat(500)).length)
        val emoji = "😀"
        val cut = ExportFileName.base("x".repeat(ExportFileName.MAX_BASE_LENGTH - 1) + emoji)
        assertEquals("x".repeat(ExportFileName.MAX_BASE_LENGTH - 1), cut)
        val kept = ExportFileName.base("x".repeat(ExportFileName.MAX_BASE_LENGTH - 2) + emoji)
        assertTrue(kept.endsWith(emoji))
    }

    @Test
    fun `a cut that ends in spaces or dots is trimmed`() {
        val title = "x".repeat(ExportFileName.MAX_BASE_LENGTH - 1) + " tail"
        assertEquals("x".repeat(ExportFileName.MAX_BASE_LENGTH - 1), ExportFileName.base(title))
    }

    @Test
    fun `unicode titles are kept`() {
        assertEquals("Lista de la compra ñ", ExportFileName.base("Lista de la compra ñ"))
    }

    @Test
    fun `formats know their extension and mime type`() {
        assertEquals("md", ExportFormat.MARKDOWN.extension)
        assertEquals("text/markdown", ExportFormat.MARKDOWN.mimeType)
        assertEquals("application/pdf", ExportFormat.PDF.mimeType)
    }
}
