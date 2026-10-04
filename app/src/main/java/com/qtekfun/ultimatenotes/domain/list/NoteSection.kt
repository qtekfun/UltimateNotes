// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.list

import java.time.YearMonth

/** The group a list row belongs to, in the order Apple Notes shows them. */
sealed interface NoteSection {
    data object Pinned : NoteSection

    data object Today : NoteSection

    data object Yesterday : NoteSection

    data object Previous7Days : NoteSection

    data object Previous30Days : NoteSection

    /** A calendar month; [includesYear] is false for months of the current year. */
    data class Month(val month: YearMonth, val includesYear: Boolean) : NoteSection

    /** The single group of a title-sorted list. */
    data object Alphabetical : NoteSection
}

/** A header and its rows. */
data class NoteGroup(val section: NoteSection, val notes: List<NoteListItem>)
