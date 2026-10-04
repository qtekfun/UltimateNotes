// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.conflict

import com.qtekfun.ultimatenotes.data.api.NoteDto
import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.model.SyncState
import com.qtekfun.ultimatenotes.domain.markdown.NoteSummary
import com.qtekfun.ultimatenotes.sync.mapper.toSyncedEntity
import java.time.Clock
import java.time.LocalDate

/**
 * Resolves the case where a note was edited locally and also changed on the server (SPEC §5).
 * Pure: it only builds the rows to store, the sync layer applies them.
 *
 * The title is a field of its own and takes part in the comparison: a local title that is not
 * blank and differs from the server's is a real change, never silently dropped.
 * - Same text and title on both sides: adopt the server's etag. If only the folder or favorite
 *   differ, the local values are kept and still have to be uploaded (state DIRTY).
 * - Different text or title: the server version stays in the original note and the local text and
 *   title become a new note titled "<title> (conflicto <date>)" in the same folder. A blank local
 *   note holds no text worth keeping, so the server version simply replaces it (unless its title
 *   was edited).
 */
class ConflictResolver(private val clock: Clock) {
    fun resolve(local: NoteEntity, server: NoteDto): Resolution {
        val serverNote = server.toSyncedEntity(local.localId)
        return when {
            local.content == serverNote.content && !retitled(local, serverNote) ->
                Resolution.Replace(adoptEtag(local, serverNote))

            local.content.isBlank() && !retitled(local, serverNote) ->
                Resolution.Replace(serverNote)

            else -> Resolution.Fork(serverNote, conflictCopy(local))
        }
    }

    /** The local title is a real edit: not blank and not what the server has. */
    private fun retitled(local: NoteEntity, server: NoteEntity): Boolean =
        local.title.isNotBlank() && local.title != server.title

    private fun adoptEtag(local: NoteEntity, server: NoteEntity): NoteEntity {
        val sameMetadata = local.category == server.category && local.favorite == server.favorite
        return local.copy(
            // Only reached when the local title is blank or already the server's.
            title = server.title,
            etag = server.etag,
            lastSyncedEtag = server.etag,
            readonly = server.readonly,
            syncState = if (sameMetadata) SyncState.SYNCED else SyncState.DIRTY
        )
    }

    private fun conflictCopy(local: NoteEntity): NoteEntity {
        val label = local.title.ifBlank { NoteSummary.firstLine(local.content) }
        return NoteEntity(
            modified = local.modified,
            title = "$label (conflicto ${LocalDate.now(clock)})".trim(),
            category = local.category,
            content = local.content,
            syncState = SyncState.NEW
        )
    }
}
