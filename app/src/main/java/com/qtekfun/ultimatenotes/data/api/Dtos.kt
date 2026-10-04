// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.api

import kotlinx.serialization.Serializable

/**
 * A note as the Notes API v1 returns it. On a list request made with `pruneBefore`, unchanged
 * notes carry only their [id], so every other field is optional.
 */
@Serializable
data class NoteDto(
    val id: Long,
    val etag: String? = null,
    val readonly: Boolean = false,
    val modified: Long = 0,
    val title: String = "",
    val category: String = "",
    val content: String? = null,
    val favorite: Boolean = false
) {
    /** Never prints the title, folder or text of the note. */
    override fun toString(): String = "NoteDto(id=$id, content=<redacted>)"
}

/** Read/write attributes sent on create and update; `null` fields are omitted (left unchanged). */
@Serializable
data class NoteWriteDto(
    val title: String? = null,
    val category: String? = null,
    val content: String? = null,
    val favorite: Boolean? = null,
    val modified: Long? = null
) {
    /** Never prints the title, folder or text of the note. */
    override fun toString(): String = "NoteWriteDto(content=<redacted>)"
}

/** App settings (API >= 1.2). */
@Serializable
data class SettingsDto(val notesPath: String = "", val fileSuffix: String = "")
