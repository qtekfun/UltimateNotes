// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.main

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatenotes.data.settings.SettingsRepository
import com.qtekfun.ultimatenotes.di.ListModule
import com.qtekfun.ultimatenotes.domain.folder.FolderOverview
import com.qtekfun.ultimatenotes.domain.folder.FolderSelection
import com.qtekfun.ultimatenotes.domain.folder.ObserveFolders
import com.qtekfun.ultimatenotes.domain.list.NoteActions
import com.qtekfun.ultimatenotes.domain.list.NoteGroup
import com.qtekfun.ultimatenotes.domain.list.NoteListItem
import com.qtekfun.ultimatenotes.domain.list.NoteSortOrder
import com.qtekfun.ultimatenotes.domain.list.ObserveNotes
import com.qtekfun.ultimatenotes.domain.list.UndoableDeletes
import com.qtekfun.ultimatenotes.domain.list.buildNoteGroups
import com.qtekfun.ultimatenotes.domain.sync.SyncTrigger
import com.qtekfun.ultimatenotes.sync.work.SyncPhase
import com.qtekfun.ultimatenotes.sync.work.SyncStatus
import com.qtekfun.ultimatenotes.sync.work.SyncStatusStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import javax.inject.Named
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Notes deleted from the list and still undoable; [token] is new for every deletion. */
data class PendingDeletion(val count: Int, val token: Long)

/** What the main screen, its folder drawer and its note list show. */
data class MainUiState(
    val selection: FolderSelection = FolderSelection.All,
    val folders: FolderOverview = FolderOverview(),
    val groups: List<NoteGroup> = emptyList(),
    /** False until the first list arrives, so the empty state does not flash on start. */
    val loaded: Boolean = false,
    val sortOrder: NoteSortOrder = NoteSortOrder.MODIFIED,
    /** Multi-select mode is on while this is not empty. */
    val selectedIds: Set<Long> = emptySet(),
    val refreshing: Boolean = false,
    /** What the background sync is doing, for the sync button in the top bar. */
    val sync: SyncStatus = SyncStatus(),
    val pendingDeletion: PendingDeletion? = null
) {
    val selecting: Boolean get() = selectedIds.isNotEmpty()

    /** True while a sync pass runs, however it was started. */
    val syncing: Boolean get() = sync.phase is SyncPhase.Syncing
}

@HiltViewModel
@Suppress("LongParameterList", "TooManyFunctions") // one intent per user action
class MainViewModel @Inject constructor(
    observeFolders: ObserveFolders,
    observeNotes: ObserveNotes,
    private val actions: NoteActions,
    private val settings: SettingsRepository,
    private val syncTrigger: SyncTrigger,
    syncStatus: SyncStatusStore,
    private val clock: Clock,
    @Named(ListModule.LIST_SCOPE) private val appScope: CoroutineScope
) : ViewModel() {
    private val selection = MutableStateFlow<FolderSelection>(FolderSelection.All)
    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val refreshing = MutableStateFlow(false)
    private val deletionCount = MutableStateFlow(0L)
    private val deletes = UndoableDeletes(appScope, UNDO_WINDOW, actions::delete)

    private val sortOrder = settings.settings.map { it.sortOrder }.distinctUntilChanged()

    /**
     * The list with everything that shaped it, so the state never mixes a new folder, order or
     * hidden set with groups built for the old one.
     */
    private data class Listing(
        val selection: FolderSelection,
        val sortOrder: NoteSortOrder,
        val groups: List<NoteGroup>,
        val hiddenCount: Int
    )

    private val listing = combine(
        observeNotes(),
        deletes.hidden,
        selection,
        sortOrder
    ) { notes, hidden, selected, order ->
        val visible = notes.filter { it.localId !in hidden }
        Listing(selected, order, buildNoteGroups(visible, selected, order, clock), hidden.size)
    }

    private val ui = combine(listing, observeFolders()) { list, folders ->
        MainUiState(
            selection = list.selection,
            folders = folders,
            groups = list.groups,
            loaded = true,
            sortOrder = list.sortOrder,
            pendingDeletion = PendingDeletion(list.hiddenCount, 0).takeIf { list.hiddenCount > 0 }
        )
    }

    val state: StateFlow<MainUiState> = combine(
        ui,
        selectedIds,
        refreshing,
        deletionCount,
        syncStatus.status
    ) { base, picked, busy, deletions, sync ->
        val listed = base.groups.flatMap { group -> group.notes.map { it.localId } }.toSet()
        base.copy(
            selectedIds = picked.intersect(listed),
            refreshing = busy || sync.phase is SyncPhase.Syncing,
            sync = sync,
            pendingDeletion = base.pendingDeletion?.copy(token = deletions)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), MainUiState())

    fun select(folder: FolderSelection) {
        selection.value = folder
        selectedIds.value = emptySet()
    }

    fun setSortOrder(order: NoteSortOrder) = settings.setSortOrder(order)

    /** Pull-to-refresh and the sync button: asks the sync layer for a pass now. */
    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                syncTrigger.requestSync()
            } finally {
                refreshing.value = false
            }
        }
    }

    // --- multi-select ---------------------------------------------------------------------------

    /** Long press: enters multi-select with [id] picked; later presses toggle. */
    fun toggleSelected(id: Long) = selectedIds.update { if (id in it) it - id else it + id }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun selectAll() {
        selectedIds.value = state.value.groups.flatMap { g -> g.notes.map(NoteListItem::localId) }
            .toSet()
    }

    // --- note actions -----------------------------------------------------------------------------

    fun toggleFavorite(note: NoteListItem) {
        viewModelScope.launch { actions.setFavorite(listOf(note.localId), !note.favorite) }
    }

    /** Favorites the picked notes, or unfavorites them when they all already are. */
    fun favoriteSelected() {
        val ids = selectedIds.value
        val picked = state.value.groups.flatMap { it.notes }.filter { it.localId in ids }
        val favorite = picked.any { !it.favorite }
        selectedIds.value = emptySet()
        viewModelScope.launch { actions.setFavorite(picked.map { it.localId }.sorted(), favorite) }
    }

    fun moveSelected(category: String) {
        val ids = selectedIds.value
        selectedIds.value = emptySet()
        viewModelScope.launch { actions.move(ids, category) }
    }

    fun delete(id: Long) = scheduleDeletion(setOf(id))

    fun deleteSelected() = scheduleDeletion(selectedIds.value)

    /** Brings back the notes of the last deletion if the undo window is still open. */
    fun undoDelete() {
        viewModelScope.launch { deletes.undo() }
    }

    private fun scheduleDeletion(ids: Set<Long>) {
        if (ids.isEmpty()) return
        selectedIds.update { it - ids }
        deletionCount.update { it + 1 }
        viewModelScope.launch { deletes.schedule(ids) }
    }

    /** Ends the undo window now, on a scope that outlives this model. */
    @VisibleForTesting
    internal fun flushDeletions() {
        appScope.launch { deletes.flush() }
    }

    override fun onCleared() = flushDeletions()

    private companion object {
        const val STOP_MS = 5_000L
        val UNDO_WINDOW = 5.seconds
    }
}
