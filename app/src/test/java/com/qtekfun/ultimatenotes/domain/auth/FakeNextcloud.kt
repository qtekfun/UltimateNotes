// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.auth

import com.qtekfun.ultimatenotes.data.auth.json
import com.qtekfun.ultimatenotes.data.auth.loginFixture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect

/**
 * A scripted Nextcloud for login tests: answers status, login start, Notes settings, poll (404 until
 * [pollsBeforeLogin] polls happened) and app password revocation.
 */
class FakeNextcloud(private val server: MockWebServer) : Dispatcher() {
    var statusBody: String = loginFixture("status.json")
    var statusCode: Int = 200
    var startCode: Int = 200
    var settingsCode: Int = 200
    var pollsBeforeLogin: Int = 1
    val requests: MutableList<String> = CopyOnWriteArrayList()
    private val polls = AtomicInteger()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        requests += "${request.method} $path"
        return when {
            path.endsWith("/status.php") -> json(statusBody, statusCode)

            path.endsWith("/index.php/login/v2") -> json(startBody(), startCode)

            path.endsWith("/login/v2/poll") ->
                if (polls.incrementAndGet() >
                    pollsBeforeLogin
                ) {
                    json(
                        loginFixture(
                            "login_result.json"
                        ).replace(
                            FIXTURE_HOST,
                            server.url("/nextcloud").toString().trimEnd('/').replace("/", "\\/")
                        )
                    )
                } else {
                    MockResponse(404)
                }

            path.endsWith("/api/v1/settings") ->
                if (settingsCode == DROP_CONNECTION) {
                    MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()
                } else {
                    json("""{"notesPath":"Notes","fileSuffix":".md"}""", settingsCode)
                }

            path.endsWith("/core/apppassword") -> MockResponse(200)

            else -> MockResponse(404)
        }
    }

    private fun startBody(): String {
        val poll = server.url("/nextcloud/login/v2/poll")
        val login = server.url("/nextcloud/login/v2/flow/abc")
        return """{"poll":{"token":"t0k3n","endpoint":"$poll"},"login":"$login"}"""
    }

    companion object {
        /** Value of [settingsCode] that makes the server cut the connection. */
        const val DROP_CONNECTION = -1

        private const val FIXTURE_HOST = "https:\\/\\/cloud.example.com"
    }
}
