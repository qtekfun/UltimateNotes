// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.widget

/** What the widget asks the app to do when it opens it. */
sealed interface LaunchRequest {
    data class OpenNote(val localId: Long) : LaunchRequest

    data object NewNote : LaunchRequest

    companion object {
        /** From the intent extras: a new note wins, then a valid (positive) note id, else null. */
        fun from(newNote: Boolean, noteId: Long?): LaunchRequest? = when {
            newNote -> NewNote
            noteId != null && noteId > 0 -> OpenNote(noteId)
            else -> null
        }
    }
}
