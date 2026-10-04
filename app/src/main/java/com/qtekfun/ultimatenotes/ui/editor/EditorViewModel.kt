// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange as FieldRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatenotes.data.local.dao.NoteDao
import com.qtekfun.ultimatenotes.data.local.model.SyncState
import com.qtekfun.ultimatenotes.di.ListModule
import com.qtekfun.ultimatenotes.domain.editor.NoteWriter
import com.qtekfun.ultimatenotes.domain.folder.FolderNode
import com.qtekfun.ultimatenotes.domain.folder.ObserveFolders
import com.qtekfun.ultimatenotes.domain.list.NoteActions
import com.qtekfun.ultimatenotes.domain.markdown.FormatAction
import com.qtekfun.ultimatenotes.domain.markdown.applyFormat
import com.qtekfun.ultimatenotes.domain.sync.SyncTrigger
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Id the list passes to open the editor on a note that does not exist yet. */
const val NEW_NOTE_ID = -1L

/** What the editor screen shows around the text. */
data class EditorUiState(
    val loaded: Boolean = false,
    /** The note to edit is gone (deleted elsewhere); the screen closes. */
    val missing: Boolean = false,
    val readOnly: Boolean = false,
    val favorite: Boolean = false,
    val category: String = "",
    val folders: List<FolderNode> = emptyList()
)

/**
 * One editing session at a time. The body lives in [text]: its content *is* the note's Markdown,
 * so what is saved is exactly what is shown. The note's title lives in [title], a single line of
 * its own (it is not part of the Markdown). Changes to either are saved ~1 s after the last
 * keystroke and when the session ends; a new note is only created once it has something in it.
 * A new note left untitled gets its title once, when it is created (see [NoteWriter.create]).
 */
@HiltViewModel
@Suppress("LongParameterList", "TooManyFunctions")
class EditorViewModel @Inject constructor(
    private val noteDao: NoteDao,
    private val writer: NoteWriter,
    private val actions: NoteActions,
    observeFolders: ObserveFolders,
    private val syncTrigger: SyncTrigger,
    @Named(ListModule.LIST_SCOPE) private val appScope: CoroutineScope
) : ViewModel() {
    /** The note being edited, as the session sees it. [noteId] is null until a new note is created. */
    private class Session(var noteId: Long?, val key: Long, val readOnly: Boolean) {
        var lastSaved: String = ""
        var lastTitle: String = ""
        var created = false

        /** The title was filled in by the app at creation and the user has not touched it since. */
        var autoTitled = false
        var wrote = false
    }

    val text = TextFieldState()

    /** The title field above the body; one line. */
    val title = TextFieldState()

    private var session: Session? = null
    private var autosave: Job? = null
    private val saveLock = Mutex()

    /** The list's actions read, change and write a whole note, so two at once could lose one. */
    private val metaLock = Mutex()
    private val meta = MutableStateFlow(EditorUiState())

    val state: StateFlow<EditorUiState> = combine(meta, observeFolders()) { base, folders ->
        base.copy(folders = folders.folders)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), EditorUiState())

    /**
     * Starts editing [noteId] ([NEW_NOTE_ID] for a new note in [category]). Does nothing if that is
     * already what is open, so a rotation does not reload the text.
     */
    fun open(noteId: Long, category: String = "") {
        if (session?.key == noteId) return
        session?.let { finish(it, text.text.toString(), title.text.toString()) }
        autosave?.cancel()
        session = null
        meta.value = EditorUiState()
        appScope.launch { load(noteId, category) }
    }

    private suspend fun load(noteId: Long, category: String) {
        if (noteId == NEW_NOTE_ID) {
            start(Session(null, noteId, readOnly = false), "", "")
            meta.value = EditorUiState(loaded = true, category = category)
            return
        }
        val note = noteDao.get(noteId)
        if (note == null || note.syncState == SyncState.DELETED) {
            meta.value = EditorUiState(loaded = true, missing = true)
            return
        }
        start(Session(note.localId, noteId, note.readonly), note.content, note.title)
        meta.value = EditorUiState(
            loaded = true,
            readOnly = note.readonly,
            favorite = note.favorite,
            category = note.category
        )
    }

    @OptIn(FlowPreview::class, ExperimentalFoundationApi::class)
    private fun start(opened: Session, content: String, noteTitle: String) {
        opened.lastSaved = content
        opened.lastTitle = noteTitle
        text.edit {
            replace(0, length, content)
            selection = FieldRange(0)
        }
        text.undoState.clearHistory()
        title.edit {
            replace(0, length, noteTitle)
            selection = FieldRange(length)
        }
        title.undoState.clearHistory()
        session = opened
        autosave = appScope.launch {
            snapshotFlow { text.text.toString() to title.text.toString() }
                .distinctUntilChanged()
                .debounce(AUTOSAVE_DELAY)
                .collect { saveNow() }
        }
    }

    /** Applies a formatting-bar [action] to the selection, as one undoable edit. */
    fun format(action: FormatAction) {
        if (session?.readOnly != false) return
        text.applyResult(applyFormat(text.text.toString(), text.domainSelection(), action))
    }

    /** Saves the text now (the screen calls this when it stops, since the process may die then). */
    suspend fun saveNow() {
        val current = session ?: return
        persist(current, text.text.toString(), title.text.toString())
    }

    /** Saves on a scope that outlives the screen: for when the app is being stopped. */
    fun saveInBackground() {
        appScope.launch { saveNow() }
    }

    /** Ends the session: saves, drops a new note left blank, and asks for a sync if anything changed. */
    fun close() {
        val current = session ?: return
        autosave?.cancel()
        session = null
        finish(current, text.text.toString(), title.text.toString())
    }

    /**
     * Ends the session because the user deletes the note: saves the text first, so that undoing the
     * deletion brings back what was on screen. Returns the note to delete, or null if it was never
     * created. The list performs the deletion and its undo.
     */
    suspend fun closeForDelete(): Long? {
        val current = session ?: return null
        autosave?.cancel()
        session = null
        persist(current, text.text.toString(), title.text.toString())
        return current.noteId
    }

    fun toggleFavorite() {
        val current = session ?: return
        if (current.readOnly) return
        val favorite = !meta.value.favorite
        meta.update { it.copy(favorite = favorite) }
        current.noteId?.let { id ->
            appScope.launch { metaLock.withLock { actions.setFavorite(listOf(id), favorite) } }
        }
    }

    fun move(category: String) {
        val current = session ?: return
        if (current.readOnly) return
        val normalized = NoteActions.normalizeCategory(category)
        meta.update { it.copy(category = normalized) }
        current.noteId?.let { id ->
            appScope.launch { metaLock.withLock { actions.move(listOf(id), normalized) } }
        }
    }

    private fun finish(ended: Session, content: String, noteTitle: String) {
        appScope.launch {
            persist(ended, content, noteTitle)
            val id = ended.noteId
            val emptied = content.isBlank() && (noteTitle.isBlank() || ended.autoTitled)
            if (ended.created && emptied && id != null) actions.delete(listOf(id))
            if (ended.wrote) syncTrigger.requestSync()
        }
    }

    private suspend fun persist(target: Session, content: String, noteTitle: String) =
        saveLock.withLock {
            val unchanged = content == target.lastSaved && noteTitle == target.lastTitle
            if (target.readOnly || unchanged) return@withLock
            val id = target.noteId
            if (id == null) {
                create(target, content, noteTitle)
            } else {
                if (noteTitle != target.lastTitle) target.autoTitled = false
                if (writer.update(id, noteTitle, content)) target.wrote = true
                target.lastSaved = content
                target.lastTitle = noteTitle
            }
        }

    @OptIn(ExperimentalFoundationApi::class)
    private suspend fun create(target: Session, content: String, noteTitle: String) {
        val created = writer.create(noteTitle, content, meta.value.category, meta.value.favorite)
            ?: return
        target.noteId = created.localId
        target.created = true
        target.wrote = true
        target.lastSaved = content
        target.lastTitle = noteTitle
        if (noteTitle.isBlank()) {
            // Show the title the note was given, unless the user has started typing one meanwhile.
            target.autoTitled = true
            target.lastTitle = created.title
            if (title.text.isEmpty()) {
                title.edit {
                    replace(0, length, created.title)
                    selection = FieldRange(length)
                }
                title.undoState.clearHistory()
            }
        }
    }

    override fun onCleared() = close()

    private companion object {
        const val STOP_MS = 5_000L
        val AUTOSAVE_DELAY = 1.seconds
    }
}
