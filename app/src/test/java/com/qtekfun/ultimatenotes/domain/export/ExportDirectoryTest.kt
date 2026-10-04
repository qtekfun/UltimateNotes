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

class ExportDirectoryTest {
    @TempDir
    lateinit var tmp: File

    @Test
    fun `write creates the folder and replaces a file of the same name`() {
        val dir = ExportDirectory(File(tmp, "exports"))
        dir.write("a.md", "one".toByteArray())
        val file = dir.write("a.md", "two".toByteArray())
        assertEquals("two", file.readText())
        assertEquals(File(tmp, "exports"), file.parentFile)
    }

    @Test
    fun `write refuses anything but a bare file name`() {
        val dir = ExportDirectory(tmp)
        assertThrows<IllegalArgumentException> { dir.write("../x.md", ByteArray(0)) }
        assertThrows<IllegalArgumentException> { dir.write("sub/x.md", ByteArray(0)) }
        assertThrows<IllegalArgumentException> { dir.write("", ByteArray(0)) }
    }

    @Test
    fun `write fails when the folder cannot be created`() {
        val blocker = File(tmp, "blocked").apply { writeText("a file, not a folder") }
        assertThrows<IllegalStateException> {
            ExportDirectory(File(blocker, "exports")).write("a.md", ByteArray(0))
        }
    }

    @Test
    fun `only files older than the cutoff are deleted`() {
        val dir = ExportDirectory(tmp)
        val old = dir.write("old.md", ByteArray(1)).also { assertTrue(it.setLastModified(1_000)) }
        val fresh = dir.write("fresh.md", ByteArray(1)).also {
            assertTrue(it.setLastModified(5_000))
        }
        File(tmp, "folder").mkdir()
        assertEquals(1, dir.deleteOlderThan(2_000))
        assertFalse(old.exists())
        assertTrue(fresh.exists())
        assertTrue(File(tmp, "folder").exists())
    }

    @Test
    fun `cleaning a folder that does not exist deletes nothing`() {
        assertEquals(0, ExportDirectory(File(tmp, "missing")).deleteOlderThan(Long.MAX_VALUE))
    }
}
