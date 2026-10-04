// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.quality

import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.model.SyncState
import kotlin.random.Random

/**
 * A made-up library that looks like a real one: sizes from a few lines to ~20 KB (most 0.5 to
 * 3 KB), about 30 folders with subfolders, 2% favorites, modified over three years, and varied
 * Markdown (headings, checklists, quotes, bold). Deterministic per index. Index 0 is the newest.
 */
object SyntheticNotes {
    private const val NOW = 1_790_000_000L
    private const val SECONDS_PER_THREE_YEARS = 3L * 365 * 24 * 3600

    private val folders = listOf(
        "", "", "", "Work", "Work/Clients", "Work/Ideas", "Home", "Home/Repairs", "Recipes",
        "Recipes/Desserts", "Travel", "Travel/2025", "Travel/2026", "Reading", "Money", "Health",
        "Garden", "Music", "Projects", "Projects/App", "Projects/Blog", "School", "Gifts",
        "Cars", "Pets", "Movies", "Ideas", "Journal", "Shopping", "Archive"
    )
    private val words = List(WORDS) { "word${it}x" } +
        listOf("groceries", "meeting", "recipe", "invoice", "holiday", "garden", "budget")

    /** The word every [SEARCH_EVERY]th note contains. */
    const val SEARCH_WORD = "invoice"
    const val SEARCH_EVERY = 25

    fun title(index: Int) = "Note %05d %s".format(index, words[index % words.size])

    fun body(index: Int): String {
        val random = Random(index)
        val target = when (random.nextInt(LONG_ONE_IN)) {
            0 -> 20_000
            in 1..SHORT_BELOW -> 200 + random.nextInt(300)
            else -> 500 + random.nextInt(2_500)
        }
        val text = StringBuilder()
        if (index % SEARCH_EVERY == 0) text.append("Remember the $SEARCH_WORD today\n")
        while (text.length < target) {
            val line = List(LINE_WORDS) { words[random.nextInt(words.size)] }.joinToString(" ")
            text.append(
                when (random.nextInt(LINE_KINDS)) {
                    0 -> "## $line"
                    1 -> "- [ ] $line"
                    2 -> "- [x] $line"
                    3 -> "> $line"
                    4 -> "**$line** and *$line* with `code`"
                    else -> line
                }
            ).append('\n')
        }
        return text.toString()
    }

    fun category(index: Int) = folders[index % folders.size]

    fun favorite(index: Int) = index % FAVORITE_EVERY == 0

    private fun modified(index: Int, total: Int) =
        NOW - index.toLong() * SECONDS_PER_THREE_YEARS / total

    fun entity(index: Int, total: Int) = NoteEntity(
        title = title(index),
        content = body(index),
        category = category(index),
        favorite = favorite(index),
        modified = modified(index, total),
        syncState = SyncState.SYNCED,
        lastSyncedEtag = "e$index"
    )

    private const val WORDS = 400
    private const val LINE_WORDS = 9
    private const val LINE_KINDS = 8
    private const val LONG_ONE_IN = 50
    private const val SHORT_BELOW = 14
    private const val FAVORITE_EVERY = 50
}
