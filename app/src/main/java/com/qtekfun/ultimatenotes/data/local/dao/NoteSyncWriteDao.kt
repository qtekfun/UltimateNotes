// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.model.SyncState

/** Writes of the sync layer (T07), safe against the user editing while a sync is in flight. */
@Dao
interface NoteSyncWriteDao {
    @Query("SELECT * FROM note WHERE localId = :localId")
    suspend fun get(localId: Long): NoteEntity?

    @Query("SELECT * FROM note WHERE id = :id")
    suspend fun getByRemoteId(id: Long): NoteEntity?

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM note WHERE localId = :localId")
    suspend fun delete(localId: Long)

    /*
     * Compare-and-set writes. The sync engine reads a note, talks to the network and then writes:
     * the user may have edited it in between, and that edit must never be overwritten. Each of
     * these runs in one transaction and writes only if the row is still exactly what the engine
     * saw ("expected"); otherwise it changes nothing and the engine leaves the note for later.
     */

    /** Replaces [expected] with [replacement] if the row is unchanged. Returns whether it did. */
    @Transaction
    suspend fun replaceIfUnchanged(expected: NoteEntity, replacement: NoteEntity): Boolean {
        if (get(expected.localId) != expected) return false
        update(replacement)
        return true
    }

    /** Deletes the row if it is unchanged. Returns whether it did. */
    @Transaction
    suspend fun deleteIfUnchanged(expected: NoteEntity): Boolean {
        if (get(expected.localId) != expected) return false
        delete(expected.localId)
        return true
    }

    /**
     * Conflict: if the row is unchanged, stores [copy] as a new row and [replacement] over the
     * original, atomically. Returns the stored copy (with its local id), or null if nothing was
     * written because the row changed.
     */
    @Transaction
    suspend fun forkIfUnchanged(
        expected: NoteEntity,
        replacement: NoteEntity,
        copy: NoteEntity
    ): NoteEntity? {
        if (get(expected.localId) != expected) return null
        val stored = copy.copy(localId = insert(copy))
        update(replacement)
        return stored
    }

    /**
     * The server accepted [pushed] (as it was when sent) and answered with [id], [etag] and the
     * title it stored ([title]: sanitized, possibly numbered). The row becomes SYNCED if it still
     * has the pushed title, text, folder and favorite, and then adopts the server's title. If it
     * was edited meanwhile it stays DIRTY on top of the new etag with its own title, and if it was
     * deleted meanwhile it stays DELETED: the newer local change is never overwritten.
     */
    @Transaction
    suspend fun completePush(pushed: NoteEntity, id: Long, etag: String, title: String) {
        val current = get(pushed.localId) ?: return
        val accepted = current.copy(id = id, etag = etag, lastSyncedEtag = etag)
        val sameBody = current.title == pushed.title &&
            current.content == pushed.content &&
            current.category == pushed.category &&
            current.favorite == pushed.favorite
        update(
            when {
                current.syncState == SyncState.DELETED -> accepted

                sameBody -> accepted.copy(
                    title = title.ifBlank { current.title },
                    syncState = SyncState.SYNCED
                )

                else -> accepted.copy(syncState = SyncState.DIRTY)
            }
        )
    }
}
