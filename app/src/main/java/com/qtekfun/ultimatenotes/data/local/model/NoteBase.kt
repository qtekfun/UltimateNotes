// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.local.model

import androidx.room3.ColumnInfo
import java.security.MessageDigest

/**
 * The mergeable fields of a note as the server last had them, as of the last successful push or
 * pull adoption: the common ancestor of a three-way merge (ADR 0011). The text is kept as a
 * SHA-256 digest, not as a second copy: the merge only needs to know whether a side still has the
 * base text, never what it was. A note without a base (never synced, or already DIRTY when the
 * column appeared) is resolved conservatively.
 */
data class NoteBase(
    @ColumnInfo(name = "baseContentHash") val contentHash: String,
    @ColumnInfo(name = "baseTitle") val title: String,
    @ColumnInfo(name = "baseCategory") val category: String,
    @ColumnInfo(name = "baseFavorite") val favorite: Boolean
) {
    /** Never prints the title or the folder (privacy: nothing of a note is logged). */
    override fun toString(): String = "NoteBase(<redacted>)"

    companion object {
        fun of(content: String, title: String, category: String, favorite: Boolean) =
            NoteBase(hash(content), title, category, favorite)

        /** Lowercase hex SHA-256 of the UTF-8 text. */
        fun hash(content: String): String = MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
