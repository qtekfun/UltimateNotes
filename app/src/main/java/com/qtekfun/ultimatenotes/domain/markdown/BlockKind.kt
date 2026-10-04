// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

/** The line-level formats the formatting bar can set. */
sealed interface BlockKind {
    data class Heading(val level: Int) : BlockKind {
        init {
            require(level in 1..MAX_HEADING) { "Invalid heading level" }
        }
    }

    data object Quote : BlockKind

    data object Bullet : BlockKind

    data object Numbered : BlockKind

    data object Checklist : BlockKind

    companion object {
        const val MAX_HEADING = 6
    }
}
