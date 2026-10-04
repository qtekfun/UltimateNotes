// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.local.entity

import androidx.room3.Entity
import androidx.room3.Fts4

/**
 * Full-text index over title and content. It is an external-content table backed by [NoteEntity]
 * (rowid = localId); Room keeps it in step with triggers, so nothing writes to it directly.
 */
@Fts4(contentEntity = NoteEntity::class)
@Entity(tableName = "note_fts")
data class NoteFtsEntity(val title: String, val content: String)
