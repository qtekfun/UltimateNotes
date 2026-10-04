// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.model.FolderCount
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes of notes. Lists hide notes deleted locally (tombstones waiting for the server)
 * and put favorites first, then the most recently modified.
 */
@Dao
interface NoteDao {
    @Query(
        "SELECT * FROM note WHERE syncState != 'DELETED' " +
            "ORDER BY favorite DESC, modified DESC, localId DESC"
    )
    fun observeAll(): Flow<List<NoteEntity>>

    /** Notes of [folder] and of all its subfolders (`folder/...`). The empty folder is "no folder". */
    @Query(
        """
        SELECT * FROM note
        WHERE syncState != 'DELETED'
          AND (category = :folder OR substr(category, 1, length(:folder) + 1) = :folder || '/')
        ORDER BY favorite DESC, modified DESC, localId DESC
        """
    )
    fun observeByFolder(folder: String): Flow<List<NoteEntity>>

    @Query(
        "SELECT * FROM note WHERE syncState != 'DELETED' AND favorite = 1 " +
            "ORDER BY modified DESC, localId DESC"
    )
    fun observeFavorites(): Flow<List<NoteEntity>>

    /** Note counts per exact category; the domain layer folds them into the folder tree. */
    @Query(
        """
        SELECT category, COUNT(*) AS noteCount FROM note
        WHERE syncState != 'DELETED'
        GROUP BY category
        ORDER BY category
        """
    )
    fun observeFolderCounts(): Flow<List<FolderCount>>

    @Query("SELECT * FROM note WHERE localId = :localId AND syncState != 'DELETED'")
    fun observe(localId: Long): Flow<NoteEntity?>

    @Query("SELECT * FROM note WHERE localId = :localId")
    suspend fun get(localId: Long): NoteEntity?

    @Query("SELECT * FROM note WHERE id = :id")
    suspend fun getByRemoteId(id: Long): NoteEntity?

    /** Inserts and returns the new localId. */
    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM note WHERE localId = :localId")
    suspend fun delete(localId: Long)
}
