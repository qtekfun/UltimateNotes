// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.auth

import mockwebserver3.MockResponse
import okhttp3.Headers.Companion.headersOf

/** A JSON response for MockWebServer. */
fun json(body: String, code: Int = 200, vararg headers: String) =
    MockResponse(code, headersOf("Content-Type", "application/json", *headers), body)
