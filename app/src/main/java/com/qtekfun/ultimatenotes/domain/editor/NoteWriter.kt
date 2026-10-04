// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.editor

import com.qtekfun.ultimatenotes.data.local.dao.NoteDao
import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.model.SyncState
import com.qtekfun.ultimatenotes.domain.list.NoteActions
import com.qtekfun.ultimatenotes.domain.markdown.NoteSummary
import java.time.Clock
import javax.inject.Inject

/** A note [NoteWriter.create] stored: its local id and the title it was given. */
data class CreatedNote(val localId: Long, val title: String) {
    /** Never prints the title. */
    override fun toString(): String = "CreatedNote(localId=$localId)"
}

/**
 * Stores what the user typed. A new note is created as NEW; editing a synced one marks it DIRTY
 * (a note that is already NEW, DIRTY or in CONFLICT keeps its state), so the sync layer uploads it.
 * Unlike the list's bulk edits, a text edit counts as a modification and bumps the date.
 *
 * The title is a field of its own (SPEC §4). Only at creation, and only if the user left it empty,
 * it is derived once from the first body line; afterwards it changes only through the title field.
 */
class NoteWriter @Inject constructor(
    private val noteDao: NoteDao,
    private val clock: Clock,
    private val defaultTitle: DefaultNoteTitle
) {
    /**
     * Creates a note. Nothing is stored (null) when both [title] and [content] are blank. An empty
     * [title] becomes the first line of [content], or the localized "New note" if it has none.
     */
    suspend fun create(
        title: String,
        content: String,
        category: String,
        favorite: Boolean
    ): CreatedNote? {
        if (title.isBlank() && content.isBlank()) return null
        val stored = title.trim().ifEmpty {
            NoteSummary.initialTitle(content).ifEmpty { defaultTitle.get() }
        }
        val note = NoteEntity(
            modified = now(),
            title = stored,
            category = NoteActions.normalizeCategory(category),
            content = content,
            favorite = favorite,
            syncState = SyncState.NEW
        )
        return CreatedNote(noteDao.insert(note), stored)
    }

    /**
     * Stores [title] and [content] for note [localId]. A blank [title] leaves the stored one as it
     * is (a note always has a title; it is never re-derived from the text). Returns whether
     * anything was written: not when nothing changed, the note is read-only, was deleted, or no
     * longer exists.
     */
    suspend fun update(localId: Long, title: String, content: String): Boolean =
        noteDao.modify(localId) { note ->
            val newTitle = title.trim().ifEmpty { note.title }
            val unchanged = note.content == content && note.title == newTitle
            if (note.readonly || note.syncState == SyncState.DELETED || unchanged) {
                null
            } else {
                note.copy(
                    content = content,
                    title = newTitle,
                    modified = now(),
                    syncState = if (note.syncState == SyncState.SYNCED) {
                        SyncState.DIRTY
                    } else {
                        note.syncState
                    }
                )
            }
        }

    private fun now(): Long = clock.instant().epochSecond
}
