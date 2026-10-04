// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.export

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.domain.export.ExportFileName
import com.qtekfun.ultimatenotes.domain.export.ExportFormat
import java.io.IOException
import kotlinx.coroutines.launch

/** What the user asked to export; the text is captured when they choose, not when it is saved. */
private class ExportJob(val format: ExportFormat, val title: String, val content: String)

/**
 * The export dialog (share or save to a file, as Markdown or PDF) and what follows from a choice.
 * Stays in the composition while hidden because the "save as" picker returns to it.
 *
 * @param title the note's current title and [content] its Markdown, read when an option is chosen.
 */
@Composable
fun ExportFlow(
    title: () -> String,
    content: () -> String,
    visible: Boolean,
    onDismiss: () -> Unit,
    viewModel: ExportViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<ExportJob?>(null) }
    val failed = stringResource(R.string.export_failed)

    fun run(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (_: IOException) {
                Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
            } catch (_: IllegalStateException) {
                Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun picked(uri: Uri?) {
        val job = pending
        pending = null
        if (uri != null && job != null) {
            run { viewModel.saveTo(uri, job.format, job.title, job.content) }
        }
    }
    val saveMarkdown = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.MARKDOWN.mimeType)
    ) { picked(it) }
    val savePdf = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.PDF.mimeType)
    ) { picked(it) }

    if (!visible) return
    fun choose(format: ExportFormat, share: Boolean) {
        val text = content()
        val name = viewModel.titleOf(title(), text)
        onDismiss()
        run {
            val job = ExportJob(format, name, text)
            if (share) {
                startShare(context, viewModel.stage(format, job.title, text))
            } else {
                pending = job
                val fileName = ExportFileName.of(job.title, format)
                (if (format == ExportFormat.PDF) savePdf else saveMarkdown).launch(fileName)
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_title)) },
        text = {
            Column {
                TextButton(onClick = { choose(ExportFormat.MARKDOWN, share = true) }) {
                    Text(stringResource(R.string.export_share_markdown))
                }
                TextButton(onClick = { choose(ExportFormat.PDF, share = true) }) {
                    Text(stringResource(R.string.export_share_pdf))
                }
                TextButton(onClick = { choose(ExportFormat.MARKDOWN, share = false) }) {
                    Text(stringResource(R.string.export_save_markdown))
                }
                TextButton(onClick = { choose(ExportFormat.PDF, share = false) }) {
                    Text(stringResource(R.string.export_save_pdf))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.export_cancel)) }
        }
    )
}

private fun startShare(context: Context, file: StagedFile) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = file.mimeType
        putExtra(Intent.EXTRA_STREAM, file.uri)
        clipData = ClipData.newRawUri(file.name, file.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}
