// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class NotesDetectorTest {
    private val server = MockWebServer()
    private lateinit var detector: NotesDetector

    @BeforeEach
    fun setUp() {
        server.start()
        detector = NotesDetector(
            NotesClientFactory({ Credentials("ana", "pw") }, Dispatchers.Unconfined)
        )
    }

    @AfterEach
    fun tearDown() = server.close()

    private suspend fun detect() = detector.detect(server.url("/").toString())

    @Test
    fun `a server answering the settings has a usable Notes app`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(200)
                .body("""{"notesPath":"Notes","fileSuffix":".md"}""")
                .build()
        )

        assertEquals(NotesAvailability.Available(SettingsDto("Notes", ".md")), detect())
        assertEquals("/index.php/apps/notes/api/v1/settings", server.takeRequest().target)
    }

    @ParameterizedTest
    @CsvSource(
        "404, NotesAppMissing",
        "400, UnsupportedApi",
        "401, Unauthorized",
        "500, Failed"
    )
    fun `maps the failures of the settings call`(code: Int, expected: String) = runTest {
        server.enqueue(MockResponse.Builder().code(code).build())

        assertEquals(expected, detect()::class.simpleName)
    }

    @Test
    fun `a server that cannot be reached is offline`() = runTest {
        server.close()

        assertEquals(NotesAvailability.Offline, detect())
    }
}
