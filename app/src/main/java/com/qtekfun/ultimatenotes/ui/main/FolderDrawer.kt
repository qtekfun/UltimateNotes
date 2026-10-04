// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.domain.folder.FolderNode
import com.qtekfun.ultimatenotes.domain.folder.FolderOverview
import com.qtekfun.ultimatenotes.domain.folder.FolderSelection
import com.qtekfun.ultimatenotes.ui.theme.UltimateNotesTheme

private val INDENT_PER_LEVEL = 16.dp

/**
 * The folder drawer (SPEC §7): All, Favorites, No folder, the category tree with counts, and
 * Settings at the bottom.
 */
@Composable
fun FolderDrawer(
    folders: FolderOverview,
    selection: FolderSelection,
    onSelect: (FolderSelection) -> Unit,
    onOpenSettings: () -> Unit
) {
    ModalDrawerSheet {
        Text(
            text = stringResource(R.string.folders_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp)
        )
        FolderList(
            folders = folders,
            selection = selection,
            onSelect = onSelect,
            modifier = Modifier.weight(1f)
        )
        Column {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp))
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.folders_settings)) },
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                selected = false,
                onClick = onOpenSettings,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun FolderList(
    folders: FolderOverview,
    selection: FolderSelection,
    onSelect: (FolderSelection) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        item {
            FolderItem(
                label = stringResource(R.string.folder_all),
                count = folders.total,
                icon = {
                    Icon(painterResource(R.drawable.ic_notes), contentDescription = null)
                },
                selected = selection == FolderSelection.All,
                onClick = { onSelect(FolderSelection.All) }
            )
        }
        item {
            FolderItem(
                label = stringResource(R.string.folder_favorites),
                count = folders.favorites,
                icon = { Icon(Icons.Default.Star, contentDescription = null) },
                selected = selection == FolderSelection.Favorites,
                onClick = { onSelect(FolderSelection.Favorites) }
            )
        }
        item {
            FolderItem(
                label = stringResource(R.string.folder_none),
                count = folders.noFolder,
                icon = {
                    Icon(painterResource(R.drawable.ic_folder), contentDescription = null)
                },
                selected = selection == FolderSelection.NoFolder,
                onClick = { onSelect(FolderSelection.NoFolder) }
            )
        }
        items(folders.folders, key = FolderNode::path) { node ->
            FolderItem(
                label = node.name,
                count = node.noteCount,
                icon = {
                    Icon(painterResource(R.drawable.ic_folder), contentDescription = null)
                },
                selected = selection == FolderSelection.Folder(node.path),
                onClick = { onSelect(FolderSelection.Folder(node.path)) },
                modifier = Modifier.padding(start = INDENT_PER_LEVEL * node.depth)
            )
        }
    }
}

@Composable
private fun FolderItem(
    label: String,
    count: Int,
    icon: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        badge = { Text(count.toString()) }
    )
}

@Preview
@Composable
@Suppress("MagicNumber")
private fun FolderDrawerPreview() {
    UltimateNotesTheme {
        FolderDrawer(
            folders = FolderOverview(
                total = 9,
                favorites = 2,
                noFolder = 3,
                folders = listOf(
                    FolderNode("Home", 0, 2),
                    FolderNode("Work", 0, 4),
                    FolderNode("Work/Clients", 1, 4)
                )
            ),
            selection = FolderSelection.Folder("Work"),
            onSelect = {},
            onOpenSettings = {}
        )
    }
}
