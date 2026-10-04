// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.editor

/** The localized title ("New note") for a note that has none and nothing to derive one from. */
fun interface DefaultNoteTitle {
    fun get(): String
}
