// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

import com.qtekfun.ultimatenotes.domain.TextRange

/**
 * Carries style runs computed for [before] over to [after], assuming one contiguous edit between
 * them (what a keystroke or a paste is). It lets big notes be analyzed off the main thread: the
 * styles drawn while the fresh analysis is still running are the previous ones, moved to where
 * their text now is.
 */
fun shiftRuns(runs: List<StyleRun>, before: String, after: String): List<StyleRun> {
    if (before == after) return runs
    val limit = minOf(before.length, after.length)
    var prefix = 0
    while (prefix < limit && before[prefix] == after[prefix]) prefix++
    var suffix = 0
    while (suffix < limit - prefix &&
        before[before.length - 1 - suffix] == after[after.length - 1 - suffix]
    ) {
        suffix++
    }
    val tailStart = before.length - suffix
    val delta = after.length - before.length
    val middleEnd = after.length - suffix

    fun map(offset: Int): Int = when {
        offset <= prefix -> offset
        offset >= tailStart -> offset + delta
        else -> minOf(offset, middleEnd)
    }
    return runs.mapNotNull { run ->
        val start = map(run.range.start)
        val end = map(run.range.end)
        if (start < end) StyleRun(TextRange(start, end), run.role) else null
    }
}
