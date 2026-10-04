// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain

/** A half-open range of UTF-16 offsets into a note's text: [start, end). */
data class TextRange(val start: Int, val end: Int) {
    init {
        require(start in 0..end) { "Invalid range" }
    }
}
