// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.local.dao

import androidx.room3.Dao
import androidx.room3.Query
import com.qtekfun.ultimatenotes.data.local.FtsQuery
import com.qtekfun.ultimatenotes.data.local.model.NoteSearchHit
import com.qtekfun.ultimatenotes.data.local.model.SearchMarkers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Offline full-text search over title and content (SPEC §7). */
@Dao
interface NoteSearchDao {
    /**
     * [match] is an FTS expression (see [FtsQuery]); [folder] limits the search to a folder and
     * its subfolders, null searches everywhere. Notes deleted locally never match.
     */
    @Query(
        """
        SELECT note.localId AS localId, note.title AS title, note.category AS category,
               note.favorite AS favorite, note.modified AS modified,
               snippet(note_fts, :open, :close, :ellipsis, -1, :tokens) AS snippet
        FROM note_fts
        JOIN note ON note.localId = note_fts.rowid
        WHERE note_fts MATCH :match
          AND note.syncState != 'DELETED'
          AND (:folder IS NULL OR note.category = :folder
               OR substr(note.category, 1, length(:folder) + 1) = :folder || '/')
        ORDER BY note.favorite DESC, note.modified DESC, note.localId DESC
        """
    )
    @Suppress("LongParameterList")
    fun observeMatches(
        match: String,
        folder: String?,
        open: String,
        close: String,
        ellipsis: String,
        tokens: Int
    ): Flow<List<NoteSearchHit>>
}

private const val SNIPPET_TOKENS = 12

/** Searches what the user typed; no results (and no query) when it has no searchable words. */
fun NoteSearchDao.search(input: String, folder: String? = null): Flow<List<NoteSearchHit>> {
    val match = FtsQuery.fromUserInput(input) ?: return flowOf(emptyList())
    return observeMatches(
        match = match,
        folder = folder,
        open = SearchMarkers.OPEN,
        close = SearchMarkers.CLOSE,
        ellipsis = SearchMarkers.ELLIPSIS,
        tokens = SNIPPET_TOKENS
    )
}
