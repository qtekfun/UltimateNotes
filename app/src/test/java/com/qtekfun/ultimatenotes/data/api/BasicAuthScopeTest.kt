// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The app password goes only to the account's own host, even when a server redirects away. */
class BasicAuthScopeTest {
    private lateinit var home: MockWebServer
    private lateinit var other: MockWebServer
    private val credentials = Credentials("alice", "app-pass")

    @BeforeEach
    fun setUp() {
        home = MockWebServer().apply { start() }
        other = MockWebServer().apply { start() }
    }

    @AfterEach
    fun tearDown() {
        home.close()
        other.close()
    }

    @Test
    fun `a request for another host is sent without credentials`() {
        other.enqueue(MockResponse.Builder().code(200).build())
        val client = OkHttpClient.Builder()
            .addInterceptor(BasicAuthInterceptor({ credentials }, host = "cloud.example.org"))
            .build()

        client.newCall(
            okhttp3.Request.Builder().url(other.url("/")).header("Authorization", "x").build()
        ).execute().close()

        assertNull(other.takeRequest().headers["Authorization"])
    }

    @Test
    fun `host matching ignores case`() {
        home.enqueue(MockResponse.Builder().code(200).build())
        val client = OkHttpClient.Builder()
            .addInterceptor(BasicAuthInterceptor({ credentials }, host = home.hostName.uppercase()))
            .build()

        client.newCall(okhttp3.Request.Builder().url(home.url("/")).build()).execute().close()

        assertNotNull(home.takeRequest().headers["Authorization"])
    }

    @Test
    fun `a redirect to another server does not carry the credentials`() = runTest {
        home.enqueue(
            MockResponse.Builder().code(302)
                .addHeader("Location", other.url("/stolen").toString()).build()
        )
        other.enqueue(MockResponse.Builder().code(200).body("[]").build())
        val client = NotesClientFactory({ credentials }, Dispatchers.Unconfined)
            .create(home.url("/").toString())

        client.listNotes()

        assertNotNull(home.takeRequest().headers["Authorization"])
        assertEquals(
            "/stolen",
            other.takeRequest().let { r ->
                assertNull(r.headers["Authorization"])
                r.target
            }
        )
    }
}
