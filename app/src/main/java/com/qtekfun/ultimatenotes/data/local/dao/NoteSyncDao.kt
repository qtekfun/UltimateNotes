// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.local.dao

import androidx.room3.Dao
import androidx.room3.Query
import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

/** Queries of the sync layer (T07): what still has to reach the server. */
@Dao
interface NoteSyncDao {
    /** Every note, tombstones included, to compare against the server list. */
    @Query("SELECT * FROM note")
    suspend fun getAll(): List<NoteEntity>

    @Query("SELECT * FROM note WHERE syncState IN ('DIRTY', 'NEW', 'DELETED') ORDER BY localId")
    suspend fun getPendingSync(): List<NoteEntity>

    @Query("SELECT * FROM note WHERE syncState = 'DIRTY' ORDER BY localId")
    suspend fun getDirty(): List<NoteEntity>

    @Query("SELECT * FROM note WHERE syncState = 'NEW' ORDER BY localId")
    suspend fun getNew(): List<NoteEntity>

    @Query("SELECT * FROM note WHERE syncState = 'DELETED' ORDER BY localId")
    suspend fun getDeleted(): List<NoteEntity>

    @Query("SELECT * FROM note WHERE syncState = 'CONFLICT' ORDER BY localId")
    suspend fun getConflicts(): List<NoteEntity>

    @Query("SELECT COUNT(*) FROM note WHERE syncState IN ('DIRTY', 'NEW', 'DELETED')")
    fun observePendingSyncCount(): Flow<Int>
}
