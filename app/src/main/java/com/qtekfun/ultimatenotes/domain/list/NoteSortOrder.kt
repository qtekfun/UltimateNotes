// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.list

/** How the note list is ordered (SPEC §7). Favorites are pinned on top whichever is chosen. */
enum class NoteSortOrder {
    /** Most recently modified first, grouped by date. The default. */
    MODIFIED,

    /** Alphabetical by title, in a single group. */
    TITLE
}
