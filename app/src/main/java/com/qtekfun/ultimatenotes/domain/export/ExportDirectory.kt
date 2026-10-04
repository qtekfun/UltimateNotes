// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.export

import java.io.File

/** The folder exported files are staged in before they are shared. */
class ExportDirectory(private val dir: File) {
    /** Writes [bytes] as [name] (a bare file name, as [ExportFileName] makes), replacing any file of that name. */
    fun write(name: String, bytes: ByteArray): File {
        require(name.isNotEmpty() && File(name).name == name) { "Not a bare file name" }
        check(dir.isDirectory || dir.mkdirs()) { "Cannot create the export folder" }
        return File(dir, name).also { it.writeBytes(bytes) }
    }

    /** Deletes staged files last modified before [cutoffMillis]; returns how many were deleted. */
    fun deleteOlderThan(cutoffMillis: Long): Int = dir.listFiles().orEmpty().count {
        it.isFile && it.lastModified() < cutoffMillis &&
            it.delete()
    }

    companion object {
        /** The sub-folder of the cache that the FileProvider exposes (see `export_paths.xml`). */
        const val NAME = "exports"

        /** Exported files older than this are removed when the app starts. */
        const val MAX_AGE_MS = 60L * 60 * 1000
    }
}
