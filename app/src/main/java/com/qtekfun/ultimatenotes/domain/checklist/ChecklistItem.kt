// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.checklist

import com.qtekfun.ultimatenotes.domain.TextRange

/**
 * A checklist line such as `- [ ] milk`.
 *
 * @property box the three characters `[ ]` / `[x]` in the source text.
 * @property checked whether the box is ticked.
 */
data class ChecklistItem(val box: TextRange, val checked: Boolean) {
    /** Offset of the single character that toggling changes (the one between the brackets). */
    val stateOffset: Int get() = box.start + 1
}
