// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.e2e

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

/** The safety guard of the E2E suite runs with the normal tests: it needs no server. */
class E2eEnvironmentTest {
    @Test
    fun `local servers are accepted`() {
        listOf(
            "http://localhost:8080",
            "http://127.0.0.1",
            "http://[::1]:8080/nextcloud",
            "HTTP://LOCALHOST"
        ).forEach {
            assertDoesNotThrow { E2eEnvironment.requireSafeTarget(it, allowRemote = false) }
        }
    }

    @Test
    fun `a remote server is refused by default`() {
        listOf("https://cloud.example.org", "http://192.168.1.10", "http://localhost.evil.test")
            .forEach {
                assertThrows<IllegalStateException> {
                    E2eEnvironment.requireSafeTarget(it, allowRemote = false)
                }
            }
    }

    @Test
    fun `a remote server is allowed with the explicit opt in`() {
        assertDoesNotThrow {
            E2eEnvironment.requireSafeTarget("https://cloud.example.org", allowRemote = true)
        }
    }

    @Test
    fun `the description never reveals the password`() {
        assertFalse("s3cret" in E2eServer("http://localhost", "admin", "s3cret").toString())
    }
}
