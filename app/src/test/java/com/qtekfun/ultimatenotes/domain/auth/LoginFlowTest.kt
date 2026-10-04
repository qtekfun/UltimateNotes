// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.auth

import com.qtekfun.ultimatenotes.data.api.NotesClientFactory
import com.qtekfun.ultimatenotes.data.api.NotesDetector
import com.qtekfun.ultimatenotes.data.auth.AccountSession
import com.qtekfun.ultimatenotes.data.auth.FakeAccountStorage
import com.qtekfun.ultimatenotes.data.auth.FakeCipher
import com.qtekfun.ultimatenotes.data.auth.LoginFlowApiFactory
import com.qtekfun.ultimatenotes.data.auth.ServerUrl
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import mockwebserver3.junit5.StartStop
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Runs in real time with short intervals: virtual time would fire the login timeout while a real
 * HTTP request is still in flight.
 */
class LoginFlowTest {
    @StartStop
    val server = MockWebServer()

    private val nextcloud by lazy { FakeNextcloud(server).also { server.dispatcher = it } }
    private val storage = FakeAccountStorage()
    private val session = AccountSession(storage, FakeCipher(), Dispatchers.IO)
    private val apiFactory = LoginFlowApiFactory(OkHttpClient(), NotesClientFactory.json)
    private val detector = NotesDetector(NotesClientFactory(session, Dispatchers.IO))
    private val flow = LoginFlow(apiFactory, session, detector, Logout(apiFactory, session))

    private fun localServer(): ServerUrl {
        nextcloud
        val parsed = ServerUrl.parse(server.url("/nextcloud/").toString(), allowInsecure = true)
        return (parsed as ServerUrl.ParseResult.Valid).url
    }

    private fun states(timeoutMs: Long = 2_000) = runBlocking {
        flow.login(
            localServer(),
            pollInterval = 10.milliseconds,
            timeout = timeoutMs.milliseconds,
            allowInsecure = true
        ).toList()
    }

    @Test
    fun `logs in through the browser and stores the account with encrypted credentials`() =
        runBlocking {
            val states = states()

            assertEquals(LoginState.CheckingServer, states[0])
            assertEquals(
                LoginState.WaitingForBrowser(server.url("/nextcloud/login/v2/flow/abc").toString()),
                states[1]
            )
            assertEquals(LoginState.LoggedIn, states[2])
            assertEquals(3, states.size)
            val account = session.activeAccount.value!!
            assertEquals(localServer().toString(), account.serverUrl)
            assertEquals("username", account.username)
            assertEquals("username", session.credentials()?.username)
            assertEquals("test-app-password", session.credentials()?.appPassword)
            val stored = storage.stored!!
            assertFalse(stored.secret.ciphertext.toString(Charsets.UTF_8).contains("test-app"))
            assertTrue("GET /nextcloud/index.php/apps/notes/api/v1/settings" in nextcloud.requests)
        }

    @Test
    fun `refuses plain http and malformed addresses without any request`() = runBlocking {
        assertEquals(
            listOf(LoginState.Failed(LoginError.INSECURE_URL)),
            flow.login("http://cloud.example.com").toList()
        )
        assertEquals(
            listOf(LoginState.Failed(LoginError.INVALID_URL)),
            flow.login("https://").toList()
        )
    }

    @Test
    fun `reports a server that is not Nextcloud`() {
        nextcloud.statusBody = """{"installed": false}"""
        assertEquals(LoginState.Failed(LoginError.NOT_NEXTCLOUD), states().last())

        nextcloud.statusBody = "<html>Welcome to nginx</html>"
        assertEquals(LoginState.Failed(LoginError.NOT_NEXTCLOUD), states().last())

        nextcloud.statusCode = 404
        assertEquals(LoginState.Failed(LoginError.NOT_NEXTCLOUD), states().last())
    }

    @Test
    fun `reports an unreachable server`() {
        val address = localServer()
        server.close()

        val states =
            runBlocking { flow.login(address, 10.milliseconds, 2_000.milliseconds).toList() }

        assertEquals(
            listOf(LoginState.CheckingServer, LoginState.Failed(LoginError.UNREACHABLE)),
            states
        )
    }

    @Test
    fun `reports a server that refuses to start the login`() {
        nextcloud.startCode = 500

        assertEquals(LoginState.Failed(LoginError.UNKNOWN), states().last())
    }

    @Test
    fun `signs out and revokes the password when the Notes app is missing`() {
        nextcloud.settingsCode = 404

        assertEquals(LoginState.Failed(LoginError.NOTES_APP_MISSING), states().last())
        assertNull(session.activeAccount.value)
        assertNull(storage.stored)
        assertTrue("DELETE /nextcloud/ocs/v2.php/core/apppassword" in nextcloud.requests)
    }

    @Test
    fun `reports a Notes app that is too old`() {
        nextcloud.settingsCode = 400

        assertEquals(LoginState.Failed(LoginError.UNSUPPORTED_API), states().last())
        assertNull(storage.stored)
    }

    @Test
    fun `reports a connection lost while checking the Notes app`() {
        nextcloud.settingsCode = FakeNextcloud.DROP_CONNECTION

        assertEquals(LoginState.Failed(LoginError.UNREACHABLE), states().last())
        assertNull(storage.stored)
    }

    @Test
    fun `reports unexpected answers of the Notes app`() {
        nextcloud.settingsCode = 500
        assertEquals(LoginState.Failed(LoginError.UNKNOWN), states().last())

        nextcloud.settingsCode = 401
        assertEquals(LoginState.Failed(LoginError.UNKNOWN), states().last())
        assertNull(storage.stored)
    }

    @Test
    fun `expires when the user never finishes in the browser`() = runBlocking {
        nextcloud.pollsBeforeLogin = Int.MAX_VALUE

        val states = states(timeoutMs = 200)

        assertEquals(LoginState.Failed(LoginError.EXPIRED), states.last())
        assertTrue(nextcloud.requests.count { it.endsWith("/login/v2/poll") } > 1)
        assertNull(session.activeAccount.value)
    }

    @Test
    fun `cancelling stops the polling`() = runBlocking {
        nextcloud.pollsBeforeLogin = Int.MAX_VALUE

        val firstWaiting = flow.login(localServer(), 10.milliseconds, 60_000.milliseconds)
            .first { it is LoginState.WaitingForBrowser }
        val pollsAtCancel = nextcloud.requests.count { it.endsWith("/login/v2/poll") }
        Thread.sleep(100)

        assertTrue(firstWaiting is LoginState.WaitingForBrowser)
        assertEquals(pollsAtCancel, nextcloud.requests.count { it.endsWith("/login/v2/poll") })
    }

    @Test
    fun `upgrades an http poll endpoint on the server's host to https`() {
        val server = (
            ServerUrl.parse(
                "https://cloud.example.com/nextcloud"
            ) as ServerUrl.ParseResult.Valid
            ).url

        assertEquals(
            "https://cloud.example.com/nextcloud/login/v2/poll",
            LoginFlow.securePollEndpoint("http://cloud.example.com/nextcloud/login/v2/poll", server)
        )
        assertEquals(
            "http://elsewhere.example/poll",
            LoginFlow.securePollEndpoint("http://elsewhere.example/poll", server)
        )
        assertEquals("not a url", LoginFlow.securePollEndpoint("not a url", server))
    }
}
