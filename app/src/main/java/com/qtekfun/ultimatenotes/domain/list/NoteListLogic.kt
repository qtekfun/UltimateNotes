// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.list

import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.domain.folder.FolderSelection
import com.qtekfun.ultimatenotes.domain.markdown.NoteSummary
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

private const val RECENT_DAYS = 7L
private const val MONTH_DAYS = 30L
private const val PREVIEW_LENGTH = 160

/** Maps a stored note to its row. The title comes from the text, as the editor shows it. */
fun NoteEntity.toListItem(): NoteListItem = NoteListItem(
    localId = localId,
    title = NoteSummary.title(content).ifEmpty { title },
    preview = NoteSummary.preview(content, PREVIEW_LENGTH),
    category = category,
    favorite = favorite,
    modified = Instant.ofEpochSecond(modified)
)

/** Whether the note is listed under [selection]; a folder includes its subfolders. */
fun NoteListItem.matches(selection: FolderSelection): Boolean = when (selection) {
    FolderSelection.All -> true

    FolderSelection.Favorites -> favorite

    FolderSelection.NoFolder -> category.isBlank()

    is FolderSelection.Folder ->
        category == selection.path || category.startsWith(selection.path + "/")
}

/** [notes] in the order of [sortOrder], without grouping. Ties fall back to newest first. */
fun sortNotes(notes: List<NoteListItem>, sortOrder: NoteSortOrder): List<NoteListItem> {
    val newestFirst = compareByDescending<NoteListItem> { it.modified }
        .thenByDescending { it.localId }
    val order = when (sortOrder) {
        NoteSortOrder.MODIFIED -> newestFirst

        NoteSortOrder.TITLE -> compareBy<NoteListItem, String>(
            String.CASE_INSENSITIVE_ORDER
        ) { it.title }.then(newestFirst)
    }
    return notes.sortedWith(order)
}

/** The section of a note last modified at [modified], by calendar days in the clock's zone. */
fun sectionOf(modified: Instant, clock: Clock): NoteSection {
    val today = LocalDate.now(clock)
    val day = modified.atZone(clock.zone).toLocalDate()
    return when {
        !day.isBefore(today) -> NoteSection.Today

        day == today.minusDays(1) -> NoteSection.Yesterday

        !day.isBefore(today.minusDays(RECENT_DAYS)) -> NoteSection.Previous7Days

        !day.isBefore(today.minusDays(MONTH_DAYS)) -> NoteSection.Previous30Days

        else -> {
            val month = YearMonth.from(day)
            NoteSection.Month(month, includesYear = month.year != today.year)
        }
    }
}

/**
 * Filters [notes] by [selection], sorts them and groups them for display. Favorites go in a
 * Pinned group on top, except when the Favorites folder itself is open (everything there is one).
 * Empty groups are omitted. Dates are judged in the clock's time zone.
 */
fun buildNoteGroups(
    notes: List<NoteListItem>,
    selection: FolderSelection,
    sortOrder: NoteSortOrder,
    clock: Clock
): List<NoteGroup> {
    val visible = sortNotes(notes.filter { it.matches(selection) }, sortOrder)
    val pinFavorites = selection != FolderSelection.Favorites
    val pinned = if (pinFavorites) visible.filter { it.favorite } else emptyList()
    val rest = if (pinFavorites) visible.filterNot { it.favorite } else visible
    val groups = when (sortOrder) {
        NoteSortOrder.TITLE -> listOf(NoteGroup(NoteSection.Alphabetical, rest))

        // Already newest first, so groupBy yields the sections in display order.
        NoteSortOrder.MODIFIED -> rest.groupBy { sectionOf(it.modified, clock) }
            .map { NoteGroup(it.key, it.value) }
    }
    return (listOf(NoteGroup(NoteSection.Pinned, pinned)) + groups).filter { it.notes.isNotEmpty() }
}
