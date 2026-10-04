// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.local.model

/** Number of live notes whose category is exactly [category] (empty = no folder). */
data class FolderCount(val category: String, val noteCount: Int)
