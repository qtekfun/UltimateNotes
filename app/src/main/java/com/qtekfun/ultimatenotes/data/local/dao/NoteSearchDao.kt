// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.local.dao

import androidx.room3.Dao
import androidx.room3.Query
import com.qtekfun.ultimatenotes.data.local.model.NoteSearchHit
import com.qtekfun.ultimatenotes.data.local.model.SearchMarkers
import com.qtekfun.ultimatenotes.domain.search.SearchQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Offline full-text search over title and content (SPEC §7). */
@Dao
interface NoteSearchDao {
    /**
     * [match] is an FTS expression (see [SearchQuery.ftsMatch]); [folder] limits the search to a folder and
     * its subfolders, null searches everywhere. Notes deleted locally never match. The snippet
     * is always taken from the content (column 1), which still gives a preview when only the
     * title matched; the title is highlighted by the caller.
     */
    @Query(
        """
        SELECT note.localId AS localId, note.title AS title, note.category AS category,
               note.favorite AS favorite, note.modified AS modified,
               snippet(note_fts, :open, :close, :ellipsis, 1, :tokens) AS snippet
        FROM note_fts
        JOIN note ON note.localId = note_fts.rowid
        WHERE note_fts MATCH :match
          AND note.syncState != 'DELETED'
          AND (:folder IS NULL OR note.category = :folder
               OR substr(note.category, 1, length(:folder) + 1) = :folder || '/')
        ORDER BY note.modified DESC, note.localId DESC
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

/** Searches [query]; [folder] limits it to a folder and its subfolders. */
fun NoteSearchDao.search(query: SearchQuery, folder: String? = null): Flow<List<NoteSearchHit>> =
    observeMatches(
        match = query.ftsMatch,
        folder = folder,
        open = SearchMarkers.OPEN,
        close = SearchMarkers.CLOSE,
        ellipsis = SearchMarkers.ELLIPSIS,
        tokens = SNIPPET_TOKENS
    )

/** Searches what the user typed; no results when it has no searchable words. */
fun NoteSearchDao.search(input: String, folder: String? = null): Flow<List<NoteSearchHit>> {
    val query = SearchQuery.parse(input) ?: return flowOf(emptyList())
    return search(query, folder)
}
