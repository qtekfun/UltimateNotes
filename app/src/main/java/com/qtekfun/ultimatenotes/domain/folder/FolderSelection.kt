// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.folder

/** What the main screen lists: the entries of the folder drawer. */
sealed interface FolderSelection {
    data object All : FolderSelection

    data object Favorites : FolderSelection

    data object NoFolder : FolderSelection

    /** A category and its subfolders; [path] is the normalized `a/b` form. */
    data class Folder(val path: String) : FolderSelection {
        /** The last segment, which is how the folder is titled. */
        val name: String get() = path.substringAfterLast('/')
    }
}
