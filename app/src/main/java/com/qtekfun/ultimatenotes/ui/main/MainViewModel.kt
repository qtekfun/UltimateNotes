// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatenotes.domain.folder.FolderOverview
import com.qtekfun.ultimatenotes.domain.folder.FolderSelection
import com.qtekfun.ultimatenotes.domain.folder.ObserveFolders
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** What the main screen and its folder drawer show. */
data class MainUiState(
    val selection: FolderSelection = FolderSelection.All,
    val folders: FolderOverview = FolderOverview()
)

@HiltViewModel
class MainViewModel @Inject constructor(observeFolders: ObserveFolders) : ViewModel() {
    private val selection = MutableStateFlow<FolderSelection>(FolderSelection.All)

    val state: StateFlow<MainUiState> = combine(selection, observeFolders()) { selected, folders ->
        MainUiState(selected, folders)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), MainUiState())

    fun select(folder: FolderSelection) {
        selection.value = folder
    }

    private companion object {
        const val STOP_MS = 5_000L
    }
}
