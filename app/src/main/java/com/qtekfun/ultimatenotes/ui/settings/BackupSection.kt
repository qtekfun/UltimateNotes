// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.domain.backup.BackupService

private const val BACKUP_MIME = "application/octet-stream"

/** Settings → Backup: save the settings and account to an encrypted file, or restore them. */
@Composable
internal fun BackupSection(viewModel: BackupViewModel = viewModel()) {
    var exporting by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf<CharArray?>(null) }
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val create = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME)
    ) { uri ->
        val secret = passphrase
        passphrase = null
        if (uri != null && secret != null) viewModel.export(uri, secret)
    }
    val fileName = stringResource(R.string.backup_file_name)
    SectionHeader(R.string.settings_backup)
    BackupRow(R.string.backup_export, R.string.backup_export_hint, !busy) { exporting = true }
    RestoreBackupRow(viewModel, enabled = !busy)
    if (exporting) {
        ExportDialog(
            onExport = { secret ->
                passphrase = secret
                exporting = false
                create.launch(fileName)
            },
            onDismiss = { exporting = false }
        )
    }
}

/** "Restore a backup" row and its passphrase dialog. Also used on the login screen. */
@Composable
internal fun RestoreBackupRow(viewModel: BackupViewModel = viewModel(), enabled: Boolean = true) {
    val context = LocalContext.current
    val asking by viewModel.askingPassphrase.collectAsStateWithLifecycle()
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::startRestore)
    }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    BackupRow(R.string.backup_restore, R.string.backup_restore_hint, enabled) {
        open.launch(arrayOf("*/*"))
    }
    if (asking) {
        RestoreDialog(onRestore = viewModel::finishRestore, onDismiss = viewModel::cancelRestore)
    }
}

/** The restore entry of the login screen, where a fresh install starts. */
@Composable
fun RestoreBackupButton(modifier: Modifier = Modifier, viewModel: BackupViewModel = viewModel()) {
    val context = LocalContext.current
    val asking by viewModel.askingPassphrase.collectAsStateWithLifecycle()
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::startRestore)
    }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    TextButton(onClick = { open.launch(arrayOf("*/*")) }, modifier = modifier) {
        Text(stringResource(R.string.login_restore_backup))
    }
    if (asking) {
        RestoreDialog(onRestore = viewModel::finishRestore, onDismiss = viewModel::cancelRestore)
    }
}

@Composable
private fun BackupRow(title: Int, hint: Int, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExportDialog(onExport: (CharArray) -> Unit, onDismiss: () -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val tooShort = first.length < BackupService.MIN_PASSPHRASE_LENGTH
    val mismatch = second.isNotEmpty() && first != second
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_export_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.backup_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                PassphraseField(
                    value = first,
                    label = R.string.backup_passphrase,
                    hint = R.string.backup_passphrase_rule,
                    error = first.isNotEmpty() && tooShort,
                    onChange = { first = it }
                )
                PassphraseField(
                    value = second,
                    label = R.string.backup_passphrase_repeat,
                    hint = R.string.backup_passphrase_mismatch.takeIf { mismatch },
                    error = mismatch,
                    onChange = { second = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onExport(first.toCharArray()) },
                enabled = !tooShort && first == second
            ) { Text(stringResource(R.string.backup_export_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun RestoreDialog(onRestore: (CharArray) -> Unit, onDismiss: () -> Unit) {
    var passphrase by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_restore_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup_restore_text))
                PassphraseField(
                    value = passphrase,
                    label = R.string.backup_passphrase,
                    hint = null,
                    error = false,
                    onChange = { passphrase = it }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onRestore(passphrase.toCharArray()) },
                enabled = passphrase.isNotEmpty()
            ) { Text(stringResource(R.string.backup_restore)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun PassphraseField(
    value: String,
    label: Int,
    hint: Int?,
    error: Boolean,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        supportingText = hint?.let {
            { Text(stringResource(it), Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        },
        isError = error,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )
}
