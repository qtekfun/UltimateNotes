// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.markdown

/** Realistic notes under `src/test/resources/corpus`, read byte for byte (CRLF included). */
object Corpus {
    val names = listOf(
        "welcome", "shopping-crlf", "unicode", "nested", "code-fences", "table", "tricky",
        "empty", "blank"
    )

    fun load(name: String): String =
        checkNotNull(Corpus::class.java.getResourceAsStream("/corpus/$name.md")) { "Missing $name" }
            .use { it.readBytes().decodeToString() }
}
