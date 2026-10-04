// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.local

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DatabaseSchemaTest {
    private val schemas = File("schemas/${UltimateNotesDatabase::class.qualifiedName}")

    @Test
    fun `every database version has its exported schema committed`() {
        (1..UltimateNotesDatabase.VERSION).forEach { version ->
            assertTrue(File(schemas, "$version.json").isFile, "missing schema for version $version")
        }
    }

    @Test
    fun `the latest exported schema matches the database version`() {
        val latest = File(schemas, "${UltimateNotesDatabase.VERSION}.json").readText()

        assertTrue(latest.contains("\"version\": ${UltimateNotesDatabase.VERSION},"))
    }

    @Test
    fun `every version after the first is reached by a migration`() {
        val steps = UltimateNotesDatabase.MIGRATIONS.map {
            it.startVersion to it.endVersion
        }.toSet()

        assertEquals((2..UltimateNotesDatabase.VERSION).map { it - 1 to it }.toSet(), steps)
    }
}
