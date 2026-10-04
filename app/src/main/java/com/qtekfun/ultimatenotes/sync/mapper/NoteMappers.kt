// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.mapper

import com.qtekfun.ultimatenotes.data.api.NoteDto
import com.qtekfun.ultimatenotes.data.api.NoteWriteDto
import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.model.SyncState

/** The server's etag; pre-1.2 servers send none, which is kept as the empty string. */
val NoteDto.localEtag: String get() = etag.orEmpty()

/**
 * A full server note as a local row in sync with it. [localId] is 0 to insert a new row, or the
 * key of the row being replaced. Fields a pruned note lacks fall back to their empty value, so
 * only call this on notes the server sent in full.
 */
fun NoteDto.toSyncedEntity(localId: Long = 0): NoteEntity = NoteEntity(
    localId = localId,
    id = id,
    etag = localEtag,
    readonly = readonly,
    modified = modified,
    title = title,
    category = category,
    content = content.orEmpty(),
    favorite = favorite,
    syncState = SyncState.SYNCED,
    lastSyncedEtag = etag
)

/**
 * What to upload for a note: every attribute the API lets clients write. The title is its own
 * field (API >= 1.0, no automatic rename from the content), so it is always sent; a blank one is
 * left out because the server would replace it with "New note". The server returns the sanitized
 * title, which the sync layer adopts.
 */
fun NoteEntity.toWriteDto(): NoteWriteDto = NoteWriteDto(
    title = title.takeIf { it.isNotBlank() },
    content = content,
    category = category,
    favorite = favorite
)
