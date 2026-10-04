// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.domain.folder.FolderNode

private val OPTION_MIN_HEIGHT = 48.dp
private const val INDENT_DP = 16

/**
 * Picks the folder to move the selected notes to: an existing one, none, or a new one typed in
 * (`a/b` for subfolders).
 */
@Composable
fun MoveDialog(folders: List<FolderNode>, onDismiss: () -> Unit, onMove: (String) -> Unit) {
    var newFolder by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_title)) },
        text = {
            Column {
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    item { FolderOption(stringResource(R.string.folder_none), 0) { onMove("") } }
                    items(folders, key = { it.path }) { node ->
                        FolderOption(node.name, node.depth) { onMove(node.path) }
                    }
                }
                OutlinedTextField(
                    value = newFolder,
                    onValueChange = { newFolder = it },
                    label = { Text(stringResource(R.string.move_new_folder)) },
                    supportingText = { Text(stringResource(R.string.move_new_folder_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onMove(newFolder) },
                enabled = newFolder.isNotBlank(),
                modifier = Modifier.heightIn(min = OPTION_MIN_HEIGHT)
            ) { Text(stringResource(R.string.move_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = OPTION_MIN_HEIGHT)) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun FolderOption(name: String, depth: Int, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = OPTION_MIN_HEIGHT)
            .clickable(onClick = onClick)
            .padding(start = (depth * INDENT_DP).dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}
