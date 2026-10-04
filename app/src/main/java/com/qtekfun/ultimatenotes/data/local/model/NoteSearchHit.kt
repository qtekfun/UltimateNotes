// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.local.model

/**
 * A search result. [snippet] is a fragment of the matching text with the matches wrapped in
 * [SearchMarkers.OPEN] and [SearchMarkers.CLOSE] so the UI can highlight them.
 */
data class NoteSearchHit(
    val localId: Long,
    val title: String,
    val category: String,
    val favorite: Boolean,
    val modified: Long,
    val snippet: String
)
