// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.local.entity

import androidx.room3.Entity
import androidx.room3.Fts4
import androidx.room3.FtsOptions

/**
 * Full-text index over title and content. It is an external-content table backed by [NoteEntity]
 * (rowid = localId); Room keeps it in step with triggers, so nothing writes to it directly.
 * The `unicode61` tokenizer makes matching case and accent insensitive for every script
 * (`cafe` finds `Café`, `ano` finds `AÑO`); the default tokenizer only folds ASCII case.
 */
@Fts4(
    contentEntity = NoteEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    tokenizerArgs = ["remove_diacritics=1"]
)
@Entity(tableName = "note_fts")
data class NoteFtsEntity(val title: String, val content: String) {
    /** Never prints the note. */
    override fun toString(): String = "NoteFtsEntity(<redacted>)"
}
