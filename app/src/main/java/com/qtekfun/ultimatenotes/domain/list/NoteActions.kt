// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.list

import com.qtekfun.ultimatenotes.data.local.dao.NoteDao
import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.model.SyncState
import javax.inject.Inject

/**
 * Edits the list makes to notes, in bulk. Each change marks a synced note DIRTY so the sync layer
 * uploads it; the modification date is left alone, so the list does not reshuffle under the user.
 */
class NoteActions @Inject constructor(private val noteDao: NoteDao) {
    suspend fun setFavorite(ids: Collection<Long>, favorite: Boolean) =
        edit(ids) { it.copy(favorite = favorite) }

    /** Moves notes to [category] (`a/b` form; blank for no folder). */
    suspend fun move(ids: Collection<Long>, category: String) {
        val normalized = normalizeCategory(category)
        edit(ids) { it.copy(category = normalized) }
    }

    /**
     * Deletes notes. One never uploaded has no server copy, so it is removed outright; any other
     * stays as a DELETED tombstone until the sync layer deletes it on the server.
     */
    suspend fun delete(ids: Collection<Long>) {
        for (note in liveNotes(ids)) {
            if (note.id == null) {
                noteDao.delete(note.localId)
            } else {
                noteDao.update(note.copy(syncState = SyncState.DELETED))
            }
        }
    }

    private suspend fun edit(ids: Collection<Long>, change: (NoteEntity) -> NoteEntity) {
        for (note in liveNotes(ids)) {
            val changed = change(note)
            if (changed == note) continue
            val state = if (note.syncState == SyncState.SYNCED) SyncState.DIRTY else note.syncState
            noteDao.update(changed.copy(syncState = state))
        }
    }

    /** The stored notes among [ids], leaving out unknown ones and tombstones. */
    private suspend fun liveNotes(ids: Collection<Long>): List<NoteEntity> =
        ids.mapNotNull { noteDao.get(it) }.filter { it.syncState != SyncState.DELETED }

    companion object {
        /** Trims segments and drops empty ones: ` a // b ` becomes `a/b`. */
        fun normalizeCategory(category: String): String =
            category.split('/').map { it.trim() }.filter { it.isNotEmpty() }.joinToString("/")
    }
}
