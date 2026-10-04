// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.link

/**
 * The one way the editor opens a link. [uri] must come from `LinkTarget.openable`. If nothing can
 * open it, the implementation tells the user without blocking; callers need not handle failure.
 */
fun interface LinkOpener {
    fun open(uri: String)
}
