// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.main

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.domain.folder.FolderNode
import com.qtekfun.ultimatenotes.domain.folder.FolderOverview
import com.qtekfun.ultimatenotes.domain.folder.FolderSelection
import com.qtekfun.ultimatenotes.domain.list.NoteSortOrder
import com.qtekfun.ultimatenotes.ui.theme.UltimateNotesTheme
import kotlinx.coroutines.launch

/** The large title with the hamburger, or the selection bar while notes are being picked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopBar(
    state: MainUiState,
    actions: ListActions,
    onOpenDrawer: () -> Unit,
    onMove: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior
) {
    val count = state.selectedIds.size
    LargeTopAppBar(
        title = {
            Text(
                if (state.selecting) {
                    pluralStringResource(R.plurals.selected_count, count, count)
                } else {
                    folderTitle(state.selection)
                }
            )
        },
        navigationIcon = {
            if (state.selecting) {
                IconButton(onClick = actions.onClearSelection) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.select_close)
                    )
                }
            } else {
                // Where Apple Notes puts "Edit" (SPEC §7).
                IconButton(onClick = onOpenDrawer) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = stringResource(R.string.folders_open)
                    )
                }
            }
        },
        actions = {
            if (state.selecting) SelectionActions(state, actions, onMove)
            MoreMenu(state, actions)
        },
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun SelectionActions(state: MainUiState, actions: ListActions, onMove: () -> Unit) {
    val allFavorite = state.groups.flatMap { it.notes }
        .filter { it.localId in state.selectedIds }
        .all { it.favorite }
    IconButton(onClick = actions.onFavoriteSelected) {
        Icon(
            if (allFavorite) Icons.Default.FavoriteBorder else Icons.Default.Favorite,
            contentDescription = stringResource(
                if (allFavorite) R.string.note_unfavorite else R.string.note_favorite
            )
        )
    }
    IconButton(onClick = onMove) {
        Icon(
            painterResource(R.drawable.ic_folder),
            contentDescription = stringResource(R.string.select_move)
        )
    }
    IconButton(onClick = actions.onDeleteSelected) {
        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.note_delete))
    }
}

/** Sort order, and select all while picking: the actions on the right of the title (SPEC §7). */
@Composable
private fun MoreMenu(state: MainUiState, actions: ListActions) {
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.list_more))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        if (state.selecting) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.select_all)) },
                onClick = {
                    open = false
                    actions.onSelectAll()
                }
            )
            HorizontalDivider()
        }
        Text(
            stringResource(R.string.list_sort_by),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
        NoteSortOrder.entries.forEach { order ->
            DropdownMenuItem(
                text = { Text(stringResource(sortLabel(order))) },
                leadingIcon = {
                    if (state.sortOrder == order) {
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                },
                onClick = {
                    open = false
                    actions.onSortOrder(order)
                },
                modifier = Modifier.semantics { selected = state.sortOrder == order }
            )
        }
    }
}

/** The settings and menu label of a sort order. */
@StringRes
fun sortLabel(order: NoteSortOrder): Int = when (order) {
    NoteSortOrder.MODIFIED -> R.string.sort_modified
    NoteSortOrder.TITLE -> R.string.sort_title
}

@Composable
private fun folderTitle(selection: FolderSelection): String = when (selection) {
    FolderSelection.All -> stringResource(R.string.folder_all)
    FolderSelection.Favorites -> stringResource(R.string.folder_favorites)
    FolderSelection.NoFolder -> stringResource(R.string.folder_none)
    is FolderSelection.Folder -> selection.name
}
