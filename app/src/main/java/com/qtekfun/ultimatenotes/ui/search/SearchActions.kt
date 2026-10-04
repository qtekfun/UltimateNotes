// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.search

/** What the search screen can do. */
data class SearchActions(
    val onQueryChange: (String) -> Unit = {},
    val onScopeChange: (SearchScope) -> Unit = {},
    val onOpenNote: (Long) -> Unit = {},
    val onClose: () -> Unit = {}
)
