// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.local.model

/** Control characters that cannot occur in a note and delimit highlighted matches in snippets. */
object SearchMarkers {
    const val OPEN = "\u0002"
    const val CLOSE = "\u0003"
    const val ELLIPSIS = "…"
}
