// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatenotes.di.SearchModule
import com.qtekfun.ultimatenotes.domain.folder.FolderSelection
import com.qtekfun.ultimatenotes.domain.search.NoteSearcher
import com.qtekfun.ultimatenotes.domain.search.SearchQuery
import com.qtekfun.ultimatenotes.domain.search.SearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Where a search looks: every note, or only the folder the list was showing. */
enum class SearchScope { ALL, FOLDER }

/** What the results area shows. */
enum class SearchStatus {
    /** Nothing searchable typed yet. */
    IDLE,

    /** A query is waiting for its results. */
    SEARCHING,

    RESULTS,

    NO_RESULTS
}

/** What the search screen shows. The query is kept only in memory and never logged. */
data class SearchUiState(
    /** True while the search screen is open over the list. */
    val active: Boolean = false,
    val query: String = "",
    val scope: SearchScope = SearchScope.ALL,
    /** The folder the "this folder" chip stands for; null when the list was not in a folder. */
    val scopeFolder: FolderSelection? = null,
    val status: SearchStatus = SearchStatus.IDLE,
    val results: List<SearchResult> = emptyList()
)

/** Full-text search of the notes: types, waits for a pause, searches and ranks. */
@HiltViewModel
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel @Inject constructor(
    private val searcher: NoteSearcher,
    @Named(SearchModule.SEARCH_DEBOUNCE) private val debounceMillis: Long
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val scope = MutableStateFlow(SearchScope.ALL)
    private val listFolder = MutableStateFlow<FolderSelection>(FolderSelection.All)
    private val active = MutableStateFlow(false)

    private val scopeFolder = listFolder.map {
        it.takeIf { selection ->
            selection is FolderSelection.Folder ||
                selection == FolderSelection.NoFolder
        }
    }

    /** The folder to search in, null for everywhere. */
    private val folder: Flow<String?> = combine(scope, scopeFolder) { scope, folder ->
        when {
            scope == SearchScope.ALL -> null
            folder is FolderSelection.Folder -> folder.path
            folder == FolderSelection.NoFolder -> ""
            else -> null
        }
    }.distinctUntilChanged()

    private class Found(val query: SearchQuery?, val results: List<SearchResult>)

    private val found: Flow<Found> = combine(
        query
            // Clearing the box answers at once; only real typing waits for a pause.
            .debounce { if (SearchQuery.parse(it) == null) 0L else debounceMillis }
            .map { SearchQuery.parse(it) }
            .distinctUntilChanged(),
        folder
    ) { parsed, folder -> parsed to folder }
        .flatMapLatest { (parsed, folder) ->
            if (parsed == null) {
                flowOf(Found(null, emptyList()))
            } else {
                searcher.search(parsed, folder).map { Found(parsed, it) }
            }
        }

    val state: StateFlow<SearchUiState> = combine(
        query,
        scope,
        scopeFolder,
        active,
        found
    ) { text, scope, folder, active, found ->
        val typed = SearchQuery.parse(text)
        val status = when {
            typed == null -> SearchStatus.IDLE
            found.query != typed -> SearchStatus.SEARCHING
            found.results.isEmpty() -> SearchStatus.NO_RESULTS
            else -> SearchStatus.RESULTS
        }
        SearchUiState(
            active = active,
            query = text,
            scope = scope,
            scopeFolder = folder,
            status = status,
            results = if (typed == null) emptyList() else found.results
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), SearchUiState())

    /** The list's current folder, which the "this folder" chip filters by. */
    fun setListFolder(selection: FolderSelection) {
        listFolder.value = selection
    }

    fun setQuery(text: String) {
        query.value = text
    }

    fun setScope(value: SearchScope) {
        scope.value = value
    }

    /** Opens the search screen over the list. */
    fun open() {
        active.value = true
    }

    /** Closes the search screen and forgets the query. */
    fun close() {
        active.value = false
        query.value = ""
        scope.value = SearchScope.ALL
    }

    private companion object {
        const val STOP_MS = 5_000L
    }
}
