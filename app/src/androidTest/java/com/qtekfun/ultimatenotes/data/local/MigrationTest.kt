// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.local

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Runs on a device (`connectedDebugAndroidTest`); `check` only compiles it. The exported schemas in
 * app/schemas are packaged as test assets. Every new database version must add a test that creates
 * the previous version, inserts a note, migrates and checks the note survived.
 */
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = InstrumentationRegistry.getInstrumentation().targetContext
            .getDatabasePath("migration-test.db"),
        driver = BundledSQLiteDriver(),
        databaseClass = UltimateNotesDatabase::class
    )

    @Test
    fun latestSchemaIsCreatedAndValidatedAgainstTheEntities() = runBlocking {
        helper.createDatabase(UltimateNotesDatabase.VERSION).close()

        helper.runMigrationsAndValidate(
            UltimateNotesDatabase.VERSION,
            UltimateNotesDatabase.MIGRATIONS.toList()
        ).use { connection ->
            val count = connection.prepare("SELECT COUNT(*) FROM note").use {
                it.step()
                it.getLong(0)
            }
            assertEquals(0L, count)
        }
    }
}
