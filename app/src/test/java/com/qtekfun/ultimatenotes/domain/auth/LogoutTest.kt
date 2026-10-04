// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.auth

import com.qtekfun.ultimatenotes.data.api.Credentials
import com.qtekfun.ultimatenotes.data.api.NotesClientFactory
import com.qtekfun.ultimatenotes.data.auth.AccountSession
import com.qtekfun.ultimatenotes.data.auth.FakeAccountStorage
import com.qtekfun.ultimatenotes.data.auth.FakeCipher
import com.qtekfun.ultimatenotes.data.auth.LoginFlowApiFactory
import com.qtekfun.ultimatenotes.data.auth.ServerUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import mockwebserver3.junit5.StartStop
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LogoutTest {
    @StartStop
    val server = MockWebServer()

    private val storage = FakeAccountStorage()
    private val session = AccountSession(storage, FakeCipher(), Dispatchers.IO)
    private val logout =
        Logout(LoginFlowApiFactory(OkHttpClient(), NotesClientFactory.json), session)

    private fun signIn() = runBlocking {
        val url = ServerUrl.parse(server.url("/nextcloud/").toString(), allowInsecure = true)
        session.signIn((url as ServerUrl.ParseResult.Valid).url, Credentials("ana", "pw"))
    }

    @Test
    fun `revokes the app password on the server and forgets the account`() = runBlocking {
        server.dispatcher = FakeNextcloud(server)
        signIn()

        logout.run(allowInsecure = true)

        val request = server.takeRequest()
        assertEquals(
            "DELETE /nextcloud/ocs/v2.php/core/apppassword",
            "${request.method} ${request.target}"
        )
        assertEquals("Basic YW5hOnB3", request.headers["Authorization"])
        assertNull(storage.stored)
        assertNull(session.activeAccount.value)
    }

    @Test
    fun `still removes the account when the server cannot be reached`() = runBlocking {
        signIn()
        server.close()

        logout.run(allowInsecure = true)

        assertNull(storage.stored)
    }

    @Test
    fun `never sends the password over plain http but still signs out`() = runBlocking {
        signIn()

        logout()

        assertEquals(0, server.requestCount)
        assertNull(storage.stored)
    }

    @Test
    fun `does nothing without an account`() = runBlocking {
        logout()

        assertEquals(0, server.requestCount)
    }
}
