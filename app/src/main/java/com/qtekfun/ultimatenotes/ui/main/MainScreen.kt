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
import com.qtekfun.ultimatenotes.ui.search.SearchActions
import com.qtekfun.ultimatenotes.ui.search.SearchScreen
import com.qtekfun.ultimatenotes.ui.search.SearchViewModel
import com.qtekfun.ultimatenotes.ui.theme.UltimateNotesTheme
import kotlinx.coroutines.launch

/** The main screen: folder drawer, large title, note list and the floating search bar. */
@Composable
fun MainScreen(
    onOpenSettings: () -> Unit,
    onOpenNote: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel(),
    searchViewModel: SearchViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val search by searchViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.selection) { searchViewModel.setListFolder(state.selection) }
    MainContent(
        state = state,
        onSelect = viewModel::select,
        onOpenSettings = onOpenSettings,
        onNewNote = { onOpenNote(NEW_NOTE_ID) },
        onSearch = searchViewModel::open,
        modifier = modifier,
        actions = ListActions(
            onOpenNote = onOpenNote,
            onRefresh = viewModel::refresh,
            onToggleFavorite = viewModel::toggleFavorite,
            onDelete = { viewModel.delete(it.localId) },
            onUndoDelete = viewModel::undoDelete,
            onToggleSelected = viewModel::toggleSelected,
            onClearSelection = viewModel::clearSelection,
            onSelectAll = viewModel::selectAll,
            onFavoriteSelected = viewModel::favoriteSelected,
            onDeleteSelected = viewModel::deleteSelected,
            onMoveSelected = viewModel::moveSelected,
            onSortOrder = viewModel::setSortOrder
        )
    )
    if (search.active) {
        SearchScreen(
            state = search,
            actions = SearchActions(
                onQueryChange = searchViewModel::setQuery,
                onScopeChange = searchViewModel::setScope,
                onOpenNote = onOpenNote,
                onClose = searchViewModel::close
            ),
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    state: MainUiState,
    onSelect: (FolderSelection) -> Unit,
    onOpenSettings: () -> Unit,
    onNewNote: () -> Unit,
    modifier: Modifier = Modifier,
    drawerState: DrawerState = rememberDrawerState(DrawerValue.Closed),
    actions: ListActions = ListActions(),
    onSearch: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var moving by rememberSaveable { mutableStateOf(false) }
    UndoSnackbar(state.pendingDeletion, snackbar, actions.onUndoDelete)
    BackHandler(enabled = state.selecting, onBack = actions.onClearSelection)
    ModalNavigationDrawer(
        drawerState = drawerState,
        modifier = modifier,
        gesturesEnabled = !state.selecting,
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
                    MainTopBar(
                        state = state,
                        actions = actions,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onMove = { moving = true },
                        scrollBehavior = scrollBehavior
                    )
                }
            ) { padding ->
                NoteList(state, actions, contentPadding = padding.withFloatingBar())
            }
            if (!state.selecting) {
                FloatingSearchBar(
                    onNewNote = onNewNote,
                    onSearch = onSearch,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
            UndoHost(snackbar, state.selecting, Modifier.align(Alignment.BottomCenter))
        }
    }
    if (moving) {
        MoveDialog(state.folders.folders, onDismiss = { moving = false }) {
            moving = false
            actions.onMoveSelected(it)
        }
    }
}

/** The snackbar, above the floating bar and the system bars. */
@Composable
private fun UndoHost(host: SnackbarHostState, selecting: Boolean, modifier: Modifier) {
    SnackbarHost(
        hostState = host,
        modifier = modifier
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                )
            )
            .padding(bottom = if (selecting) 0.dp else FloatingSearchBarHeight)
    )
}

/** Shows the undo snackbar while a deletion is pending; it goes away when the window closes. */
@Composable
private fun UndoSnackbar(pending: PendingDeletion?, host: SnackbarHostState, onUndo: () -> Unit) {
    val message = pending?.let { pluralStringResource(R.plurals.notes_deleted, it.count, it.count) }
    val undo = stringResource(R.string.undo)
    LaunchedEffect(pending?.token) {
        if (message != null) {
            val result = host.showSnackbar(message, undo, duration = SnackbarDuration.Indefinite)
            if (result == SnackbarResult.ActionPerformed) onUndo()
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

@Preview
@Composable
@Suppress("MagicNumber")
private fun MainContentPreview() {
    UltimateNotesTheme {
        MainContent(
            state = MainUiState(
                FolderSelection.All,
                FolderOverview(total = 3, folders = listOf(FolderNode("Work", 0, 3))),
                loaded = true
            ),
            onSelect = {},
            onOpenSettings = {},
            onNewNote = {}
        )
    }
}
