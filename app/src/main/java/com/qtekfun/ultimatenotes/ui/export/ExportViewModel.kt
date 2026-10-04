// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import com.qtekfun.ultimatenotes.data.local.dao.NoteDao
import com.qtekfun.ultimatenotes.domain.export.ExportDirectory
import com.qtekfun.ultimatenotes.domain.export.ExportFileName
import com.qtekfun.ultimatenotes.domain.export.ExportFormat
import com.qtekfun.ultimatenotes.domain.markdown.NoteSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** A file ready to hand to another app: its content [uri] (read-only grant) and what it is. */
class StagedFile(val uri: Uri, val mimeType: String, val name: String)

/** Renders a note to a file and stages it in the cache or writes it where the user chose. */
@HiltViewModel
class ExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val noteDao: NoteDao,
    private val pdf: PdfExporter,
    private val io: CoroutineDispatcher
) : ViewModel() {
    /** The stored title of [noteId], or the note's first line when it has none (or is unsaved). */
    suspend fun titleOf(noteId: Long, content: String): String =
        noteDao.get(noteId)?.title?.takeIf { it.isNotBlank() } ?: NoteSummary.title(content)

    /** The file's bytes: the Markdown source unchanged, or the rendered PDF. */
    suspend fun render(format: ExportFormat, title: String, content: String): ByteArray =
        withContext(io) {
            when (format) {
                ExportFormat.MARKDOWN -> content.toByteArray(Charsets.UTF_8)
                ExportFormat.PDF -> pdf.render(title, content)
            }
        }

    /** Writes the note into the shareable cache folder and returns a URI other apps may read. */
    suspend fun stage(format: ExportFormat, title: String, content: String): StagedFile {
        val bytes = render(format, title, content)
        val name = ExportFileName.of(title, format)
        return withContext(io) {
            val file = ExportDirectory(
                File(context.cacheDir, ExportDirectory.NAME)
            ).write(name, bytes)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
            StagedFile(uri, format.mimeType, name)
        }
    }

    /** Writes the note to [target], a document the user picked. */
    suspend fun saveTo(target: Uri, format: ExportFormat, title: String, content: String) {
        val bytes = render(format, title, content)
        withContext(io) {
            val stream = context.contentResolver.openOutputStream(target, "wt")
                ?: throw IOException("Cannot open the destination")
            stream.use { it.write(bytes) }
        }
    }
}
