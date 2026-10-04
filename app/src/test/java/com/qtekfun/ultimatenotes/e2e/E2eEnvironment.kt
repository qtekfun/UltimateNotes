// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.e2e

import com.qtekfun.ultimatenotes.data.api.ApiResult
import com.qtekfun.ultimatenotes.data.api.Credentials
import com.qtekfun.ultimatenotes.data.api.CredentialsProvider
import com.qtekfun.ultimatenotes.data.api.NoteDto
import com.qtekfun.ultimatenotes.data.api.NotesClient
import com.qtekfun.ultimatenotes.data.api.NotesClientFactory
import com.qtekfun.ultimatenotes.data.api.NotesListing
import java.net.URI
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assumptions.assumeTrue

/** Where the E2E suite points, read from the environment (never from a file, never logged). */
class E2eServer(val url: String, val user: String, private val password: String) {
    fun newClient(): NotesClient = NotesClientFactory(
        CredentialsProvider { Credentials(user, password) },
        Dispatchers.IO
    ).create(url)

    override fun toString() = "E2eServer(url=$url, user=$user, password=***)"
}

object E2eEnvironment {
    /** Every note, folder and conflict copy the suite creates starts with this. */
    const val PREFIX = "[test]"

    private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "::1", "[::1]")

    /**
     * Refuses a server that is not on this machine unless [allowRemote] (`E2E_ALLOW_REMOTE=1`):
     * the suite creates and deletes notes, so it must never hit someone's real server by accident.
     */
    fun requireSafeTarget(url: String, allowRemote: Boolean) {
        val host = URI(url.trim()).host.orEmpty().lowercase()
        check(host in LOCAL_HOSTS || allowRemote) {
            "Refusing to run the E2E suite against $host: set E2E_ALLOW_REMOTE=1 to allow it"
        }
    }

    /** Skips the calling test (not a failure) when `NC_URL`/`NC_USER`/`NC_APP_PASSWORD` are unset. */
    fun server(): E2eServer {
        val url = System.getenv("NC_URL").orEmpty()
        val user = System.getenv("NC_USER").orEmpty()
        val password = System.getenv("NC_APP_PASSWORD").orEmpty()
        assumeTrue(
            url.isNotBlank() && user.isNotBlank() && password.isNotBlank(),
            "E2E skipped: set NC_URL, NC_USER and NC_APP_PASSWORD (see e2e/setup.sh)"
        )
        requireSafeTarget(url, allowRemote = System.getenv("E2E_ALLOW_REMOTE") == "1")
        return E2eServer(url, user, password)
    }

    /** The whole server list, as the real API returns it. */
    suspend fun serverNotes(client: NotesClient): List<NoteDto> {
        val listing = client.listNotes()
        check(listing is ApiResult.Success) { "listing failed: $listing" }
        return (listing.value as NotesListing.Page).page.notes
    }

    /** Deletes every `[test]` note (by title or folder) through the API. */
    suspend fun purge(client: NotesClient) {
        serverNotes(client)
            .filter { it.title.startsWith(PREFIX) || it.category.startsWith(PREFIX) }
            .forEach { client.deleteNote(it.id) }
    }
}
