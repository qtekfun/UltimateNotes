// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.auth

import com.qtekfun.ultimatenotes.data.api.Credentials
import java.security.GeneralSecurityException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AccountSessionTest {
    private val storage = FakeAccountStorage()
    private val cipher = FakeCipher()
    private val session = AccountSession(storage, cipher, Dispatchers.Unconfined)
    private val server =
        (ServerUrl.parse("https://cloud.example.com/nextcloud") as ServerUrl.ParseResult.Valid).url

    @Test
    fun `signing in stores the password only encrypted and exposes the credentials`() = runTest {
        session.signIn(server, Credentials("ana", "app-pässwörd-123"))

        val stored = storage.stored!!
        assertEquals("https://cloud.example.com/nextcloud/", stored.serverUrl)
        assertEquals("ana", stored.username)
        assertFalse(stored.secret.ciphertext.toString(Charsets.UTF_8).contains("pässwörd"))
        assertEquals(
            Account("https://cloud.example.com/nextcloud/", "ana"),
            session.activeAccount.value
        )
        assertEquals("app-pässwörd-123", session.credentials()?.appPassword)
    }

    @Test
    fun `a new session restores the account and decrypts lazily for the network`() = runTest {
        session.signIn(server, Credentials("ana", "secret"))
        val coldStart = AccountSession(storage, cipher, Dispatchers.Unconfined)

        assertEquals("secret", coldStart.credentials()?.appPassword)
        assertEquals("ana", coldStart.activeAccount.value?.username)
        assertEquals("ana", coldStart.restore()?.username)
    }

    @Test
    fun `restore without an account returns null`() = runTest {
        assertNull(session.restore())
        assertNull(session.credentials())
    }

    @Test
    fun `an unreadable password (lost Keystore key) clears the account`() = runTest {
        session.signIn(server, Credentials("ana", "secret"))
        val broken = object : SecretCipher {
            override fun encrypt(plaintext: ByteArray): EncryptedSecret = error("unused")

            override fun decrypt(secret: EncryptedSecret): ByteArray =
                throw GeneralSecurityException("key gone")
        }
        val coldStart = AccountSession(storage, broken, Dispatchers.Unconfined)

        assertNull(coldStart.restore())
        assertNull(storage.stored)
        assertNull(coldStart.credentials())
    }

    @Test
    fun `signing out forgets everything`() = runTest {
        session.signIn(server, Credentials("ana", "secret"))

        session.signOut()

        assertNull(storage.stored)
        assertNull(session.activeAccount.value)
        assertNull(session.credentials())
    }
}
