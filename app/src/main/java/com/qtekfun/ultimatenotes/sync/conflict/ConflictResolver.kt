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
 * - Same text on both sides: adopt the server's etag. If only the folder or favorite differ, the
 *   local values are kept and still have to be uploaded (state DIRTY).
 * - Different text: the server version stays in the original note and the local text becomes a new
 *   note titled "<title> (conflicto <date>)" in the same folder. A blank local note holds no text
 *   worth keeping, so the server version simply replaces it.
 */
class ConflictResolver(private val clock: Clock) {
    fun resolve(local: NoteEntity, server: NoteDto): Resolution {
        val serverNote = server.toSyncedEntity(local.localId)
        return when {
            local.content == serverNote.content -> Resolution.Replace(adoptEtag(local, serverNote))
            local.content.isBlank() -> Resolution.Replace(serverNote)
            else -> Resolution.Fork(serverNote, conflictCopy(local))
        }
    }

    private fun adoptEtag(local: NoteEntity, server: NoteEntity): NoteEntity {
        val sameMetadata = local.category == server.category && local.favorite == server.favorite
        return local.copy(
            etag = server.etag,
            lastSyncedEtag = server.etag,
            readonly = server.readonly,
            syncState = if (sameMetadata) SyncState.SYNCED else SyncState.DIRTY
        )
    }

    private fun conflictCopy(local: NoteEntity): NoteEntity {
        val content = withFirstLineSuffix(local.content, " (conflicto ${LocalDate.now(clock)})")
        return NoteEntity(
            modified = local.modified,
            title = NoteSummary.title(content),
            category = local.category,
            content = content,
            syncState = SyncState.NEW
        )
    }

    /** Appends [suffix] to the first non-blank line, which is where the title comes from. */
    private fun withFirstLineSuffix(content: String, suffix: String): String {
        val lines = content.split("\n").toMutableList()
        val index = lines.indexOfFirst { it.isNotBlank() }
        val body = lines[index].trimEnd('\r')
        lines[index] = body + suffix + lines[index].substring(body.length)
        return lines.joinToString("\n")
    }
}
