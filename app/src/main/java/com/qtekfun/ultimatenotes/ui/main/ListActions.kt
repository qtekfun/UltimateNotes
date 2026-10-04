// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.main

import com.qtekfun.ultimatenotes.domain.list.NoteListItem
import com.qtekfun.ultimatenotes.domain.list.NoteSortOrder

/** Everything the note list can ask for; all do nothing by default (previews, tests). */
data class ListActions(
    val onOpenNote: (Long) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onToggleFavorite: (NoteListItem) -> Unit = {},
    val onDelete: (NoteListItem) -> Unit = {},
    val onUndoDelete: () -> Unit = {},
    val onToggleSelected: (Long) -> Unit = {},
    val onClearSelection: () -> Unit = {},
    val onSelectAll: () -> Unit = {},
    val onFavoriteSelected: () -> Unit = {},
    val onDeleteSelected: () -> Unit = {},
    val onMoveSelected: (String) -> Unit = {},
    val onSortOrder: (NoteSortOrder) -> Unit = {}
)
