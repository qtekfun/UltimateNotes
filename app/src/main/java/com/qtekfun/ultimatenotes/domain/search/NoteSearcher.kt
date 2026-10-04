// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.search

import com.qtekfun.ultimatenotes.data.local.dao.NoteSearchDao
import com.qtekfun.ultimatenotes.data.local.dao.search
import com.qtekfun.ultimatenotes.data.local.model.NoteSearchHit
import com.qtekfun.ultimatenotes.di.ListModule
import java.time.Instant
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** One search result: the note, its highlighted title and a highlighted fragment of its text. */
data class SearchResult(
    val localId: Long,
    /** May be empty for an untitled note. */
    val title: Highlighted,
    val snippet: Highlighted,
    /** The folder, `a/b` form; empty for no folder. */
    val category: String,
    val favorite: Boolean,
    val modified: Instant
)

/** Searches the notes on the device; results follow the notes as they change. */
fun interface NoteSearcher {
    /** [folder] limits the search to that folder and its subfolders; null searches every note. */
    fun search(query: SearchQuery, folder: String?): Flow<List<SearchResult>>
}

/**
 * Results with a title match come first (the more words of the query in the title, the higher),
 * then the most recently modified. That is what "relevance" means for a notes app: a note named
 * after what you look for beats one that mentions it in passing.
 */
internal fun rankSearchResults(results: List<SearchResult>): List<SearchResult> =
    results.sortedWith(
        compareByDescending<SearchResult> { it.title.ranges.size }
            .thenByDescending { it.modified }
            .thenByDescending { it.localId }
    )

internal fun NoteSearchHit.toResult(query: SearchQuery) = SearchResult(
    localId = localId,
    title = Highlighted(title, Highlight.matches(title, query)),
    snippet = Highlight.snippet(snippet),
    category = category,
    favorite = favorite,
    modified = Instant.ofEpochSecond(modified)
)

/** [NoteSearcher] over the Room full-text index. */
class RoomNoteSearcher @Inject constructor(
    private val dao: NoteSearchDao,
    @Named(ListModule.LIST_DISPATCHER) private val dispatcher: CoroutineDispatcher
) : NoteSearcher {
    override fun search(query: SearchQuery, folder: String?): Flow<List<SearchResult>> =
        dao.search(query, folder)
            .map { hits -> rankSearchResults(hits.map { it.toResult(query) }) }
            .flowOn(dispatcher)
}
