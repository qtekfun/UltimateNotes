// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.list

import java.time.Instant

/** One row of the list: what it shows, derived from a stored note. */
data class NoteListItem(
    val localId: Long,
    val title: String,
    val preview: String,
    /** The folder, `a/b` form; empty for no folder. */
    val category: String,
    val favorite: Boolean,
    val modified: Instant
) {
    /** Never prints the title, preview or folder of the note. */
    override fun toString(): String = "NoteListItem(localId=$localId, content=<redacted>)"
}
