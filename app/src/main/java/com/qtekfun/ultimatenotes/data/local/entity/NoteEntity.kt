// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.local.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.qtekfun.ultimatenotes.data.local.model.SyncState

/**
 * A note: the Nextcloud Notes API model (SPEC §4) plus local sync fields.
 *
 * [localId] is the stable local key. [id] is the server id, null until a NEW note is uploaded.
 * [modified] is in epoch seconds, as the API sends it. [category] is the folder, with `/` for
 * subfolders; empty means no folder. [lastSyncedEtag] is the ETag of the server version this copy
 * was last in sync with, null if never synced.
 */
@Entity(
    tableName = "note",
    indices = [Index("id", unique = true), Index("syncState"), Index("category")]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val id: Long? = null,
    val etag: String = "",
    val readonly: Boolean = false,
    val modified: Long = 0,
    val title: String = "",
    val category: String = "",
    val content: String = "",
    val favorite: Boolean = false,
    val syncState: SyncState = SyncState.NEW,
    val lastSyncedEtag: String? = null
)
