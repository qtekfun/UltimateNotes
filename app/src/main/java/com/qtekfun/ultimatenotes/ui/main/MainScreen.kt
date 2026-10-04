// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.domain.folder.FolderNode
import com.qtekfun.ultimatenotes.domain.folder.FolderOverview
import com.qtekfun.ultimatenotes.domain.folder.FolderSelection
import com.qtekfun.ultimatenotes.ui.theme.UltimateNotesTheme
import kotlinx.coroutines.launch

/** The main screen: folder drawer, large title and the floating search bar. */
@Composable
fun MainScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MainContent(
        state = state,
        onSelect = viewModel::select,
        onOpenSettings = onOpenSettings,
        onNewNote = {}, // T11
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    state: MainUiState,
    onSelect: (FolderSelection) -> Unit,
    onOpenSettings: () -> Unit,
    onNewNote: () -> Unit,
    modifier: Modifier = Modifier,
    drawerState: DrawerState = rememberDrawerState(DrawerValue.Closed)
) {
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        modifier = modifier,
        drawerContent = {
            FolderDrawer(
                folders = state.folders,
                selection = state.selection,
                onSelect = {
                    onSelect(it)
                    scope.launch { drawerState.close() }
                },
                onOpenSettings = {
                    scope.launch { drawerState.close() }
                    onOpenSettings()
                }
            )
        }
    ) {
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = {
                    LargeTopAppBar(
                        title = { Text(folderTitle(state.selection)) },
                        navigationIcon = {
                            // Where Apple Notes puts "Edit" (SPEC §7).
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    Icons.Default.Menu,
                                    contentDescription = stringResource(R.string.folders_open)
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior
                    )
                }
            ) { padding ->
                NoteListPlaceholder(contentPadding = padding.withFloatingBar()) // T10
            }
            FloatingSearchBar(
                onNewNote = onNewNote,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/** Keeps the end of the list clear of the floating bar. */
@Composable
private fun PaddingValues.withFloatingBar(): PaddingValues {
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(direction),
        top = calculateTopPadding(),
        end = calculateEndPadding(direction),
        bottom = calculateBottomPadding() + FloatingSearchBarHeight
    )
}

/** Empty state until the note list arrives (T10). Scrollable, so the large title collapses. */
@Composable
private fun NoteListPlaceholder(contentPadding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        item {
            Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.main_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun folderTitle(selection: FolderSelection): String = when (selection) {
    FolderSelection.All -> stringResource(R.string.folder_all)
    FolderSelection.Favorites -> stringResource(R.string.folder_favorites)
    FolderSelection.NoFolder -> stringResource(R.string.folder_none)
    is FolderSelection.Folder -> selection.name
}

@Preview
@Composable
@Suppress("MagicNumber")
private fun MainContentPreview() {
    UltimateNotesTheme {
        MainContent(
            state = MainUiState(
                FolderSelection.All,
                FolderOverview(total = 3, folders = listOf(FolderNode("Work", 0, 3)))
            ),
            onSelect = {},
            onOpenSettings = {},
            onNewNote = {}
        )
    }
}
