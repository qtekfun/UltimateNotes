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

/**
 * Stores what the user typed. A new note is created as NEW; editing a synced one marks it DIRTY
 * (a note that is already NEW, DIRTY or in CONFLICT keeps its state), so the sync layer uploads it.
 * Unlike the list's bulk edits, a text edit counts as a modification and bumps the date.
 */
class NoteWriter @Inject constructor(private val noteDao: NoteDao, private val clock: Clock) {
    /** Creates a note and returns its local id; null (and nothing stored) when [content] is blank. */
    suspend fun create(content: String, category: String, favorite: Boolean): Long? {
        if (content.isBlank()) return null
        val note = NoteEntity(
            modified = now(),
            title = NoteSummary.title(content),
            category = NoteActions.normalizeCategory(category),
            content = content,
            favorite = favorite,
            syncState = SyncState.NEW
        )
        return noteDao.insert(note)
    }

    /**
     * Stores [content] as the text of note [localId]. Returns whether anything was written: not
     * when the text is unchanged, the note is read-only, was deleted, or no longer exists.
     */
    suspend fun update(localId: Long, content: String): Boolean = noteDao.modify(localId) { note ->
        if (note.readonly || note.syncState == SyncState.DELETED || note.content == content) {
            null
        } else {
            note.copy(
                content = content,
                title = NoteSummary.title(content),
                modified = now(),
                syncState = if (note.syncState ==
                    SyncState.SYNCED
                ) {
                    SyncState.DIRTY
                } else {
                    note.syncState
                }
            )
        }
    }

    private fun now(): Long = clock.instant().epochSecond
}
