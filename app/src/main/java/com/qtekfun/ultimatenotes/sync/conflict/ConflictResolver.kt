// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.conflict

import com.qtekfun.ultimatenotes.data.api.NoteDto
import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.model.NoteBase
import com.qtekfun.ultimatenotes.data.local.model.SyncState
import com.qtekfun.ultimatenotes.domain.markdown.NoteSummary
import com.qtekfun.ultimatenotes.sync.mapper.toSyncedEntity
import java.time.Clock
import java.time.LocalDate

/**
 * Resolves the case where a note was edited locally and also changed on the server (SPEC §5,
 * ADR 0011). Pure: it only builds the rows to store, the sync layer applies them.
 *
 * With a merge base (the server's fields as of the last sync) it is a field-wise three-way merge:
 * a field only one side changed takes that side's value, and the same change on both sides merges
 * silently. Only when both sides changed the text or the title to different values is there a
 * conflict: the server version stays in the original note and the local text and title become a new
 * note titled "<title> (conflicto <date>)" in the same folder, so nothing is lost. Folder and
 * favorite are never a conflict: if both sides changed them differently the local value wins, as
 * the user's latest intent. A blank local text holds nothing worth keeping, so the server text
 * replaces it unless the title conflicts.
 *
 * Without a base (never synced, or already edited when the base was introduced) it falls back to
 * the conservative rule: same text and title on both sides adopt the server's etag (a differing
 * folder or favorite stays local and DIRTY), anything else is a conflict. A local title that is not
 * blank and differs from the server's counts as a real change.
 */
class ConflictResolver(private val clock: Clock) {
    fun resolve(local: NoteEntity, server: NoteDto): Resolution {
        val serverNote = server.toSyncedEntity(local.localId)
        val base = local.base
        return if (base == null) {
            withoutBase(local, serverNote)
        } else {
            merge(local, serverNote, base)
        }
    }

    private fun withoutBase(local: NoteEntity, serverNote: NoteEntity): Resolution = when {
        local.content == serverNote.content && !retitled(local, serverNote) ->
            Resolution.Replace(adoptEtag(local, serverNote))

        local.content.isBlank() && !retitled(local, serverNote) ->
            Resolution.Replace(serverNote)

        else -> Resolution.Fork(serverNote, conflictCopy(local))
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
            base = server.base,
            syncState = if (sameMetadata) SyncState.SYNCED else SyncState.DIRTY
        )
    }

    private fun merge(local: NoteEntity, serverNote: NoteEntity, base: NoteBase): Resolution {
        // A blank local title is "not edited" (the app never saves one for an existing note).
        val title = merge3(local.title.ifBlank { base.title }, serverNote.title, base.title)
        // A blank local text holds nothing worth a copy: the server's replaces it.
        val content = mergeContent(local, serverNote, base)
            ?: serverNote.content.takeIf { local.content.isBlank() && title != null }
        // Folder and favorite: the local value wins unless it is still the base.
        val category = if (local.category == base.category) serverNote.category else local.category
        val favorite = if (local.favorite == base.favorite) serverNote.favorite else local.favorite
        val metadataPending = category != serverNote.category || favorite != serverNote.favorite
        return if (title == null || content == null) {
            // The server's text and title stay; the local folder/favorite choices still survive.
            Resolution.Fork(
                serverNote.withMetadata(category, favorite, metadataPending, local.modified),
                conflictCopy(local)
            )
        } else {
            val pending = metadataPending || title != serverNote.title ||
                content != serverNote.content
            Resolution.Replace(
                serverNote.copy(
                    title = title,
                    content = content,
                    category = category,
                    favorite = favorite,
                    modified = if (pending) {
                        maxOf(local.modified, serverNote.modified)
                    } else {
                        serverNote.modified
                    },
                    syncState = if (pending) SyncState.DIRTY else SyncState.SYNCED
                )
            )
        }
    }

    private fun NoteEntity.withMetadata(
        category: String,
        favorite: Boolean,
        pending: Boolean,
        localModified: Long
    ): NoteEntity = if (pending) {
        copy(
            category = category,
            favorite = favorite,
            modified = maxOf(localModified, modified),
            syncState = SyncState.DIRTY
        )
    } else {
        this
    }

    /** The merged text, or null if both sides changed it to different texts. */
    private fun mergeContent(local: NoteEntity, server: NoteEntity, base: NoteBase): String? {
        val localHash = NoteBase.hash(local.content)
        return when (merge3(localHash, NoteBase.hash(server.content), base.contentHash)) {
            null -> null
            localHash -> local.content
            else -> server.content
        }
    }

    /** The value that survives: the changed side's (either if they agree); null if they clash. */
    private fun <T : Any> merge3(local: T, server: T, base: T): T? = when {
        local == base -> server
        server == base || local == server -> local
        else -> null
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
