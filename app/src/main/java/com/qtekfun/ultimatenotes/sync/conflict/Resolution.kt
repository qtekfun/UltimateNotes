// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.conflict

import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity

/** What to do with a local note whose server version changed under it. */
sealed interface Resolution {
    /** Replace the local row with [note]; nothing else to store. */
    data class Replace(val note: NoteEntity) : Resolution

    /**
     * Keep the server version in the original row ([original]) and store the local text as the
     * new note [copy] (state NEW, same folder), so no text is lost.
     */
    data class Fork(val original: NoteEntity, val copy: NoteEntity) : Resolution
}
