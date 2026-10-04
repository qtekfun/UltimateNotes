// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.backup

import com.qtekfun.ultimatenotes.data.api.Credentials
import com.qtekfun.ultimatenotes.data.auth.AccountSession
import com.qtekfun.ultimatenotes.data.auth.FakeAccountStorage
import com.qtekfun.ultimatenotes.data.auth.FakeCipher
import com.qtekfun.ultimatenotes.data.auth.ServerUrl
import com.qtekfun.ultimatenotes.data.backup.PassphraseBackupCipher
import com.qtekfun.ultimatenotes.data.settings.AppSettings
import com.qtekfun.ultimatenotes.data.settings.FakePreferences
import com.qtekfun.ultimatenotes.data.settings.SettingsRepository
import com.qtekfun.ultimatenotes.data.settings.SyncInterval
import com.qtekfun.ultimatenotes.data.settings.SyncNetwork
import com.qtekfun.ultimatenotes.data.settings.ThemeMode
import com.qtekfun.ultimatenotes.domain.list.NoteSortOrder
import com.qtekfun.ultimatenotes.domain.lock.LockTimeout
import java.security.GeneralSecurityException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupServiceTest {
    private val codec = PassphraseBackupCipher(iterations = 1_000)
    private val passphrase = "correct horse".toCharArray()

    /** A phone: its own storage, preferences and session. */
    private class Phone(
        codec: PassphraseBackupCipher,
        cipher: com.qtekfun.ultimatenotes.data.auth.SecretCipher = FakeCipher()
    ) {
        val storage = FakeAccountStorage()
        val preferences = FakePreferences()
        val settings = SettingsRepository(preferences)
        val session = AccountSession(storage, cipher, Dispatchers.Unconfined)
        val service = BackupService(codec, settings, session, Dispatchers.Unconfined)
    }

    private fun server(url: String) = (ServerUrl.parse(url) as ServerUrl.ParseResult.Valid).url

    private suspend fun Phone.signIn(
        url: String = "https://cloud.example.com/nextcloud",
        user: String = "ana",
        password: String = "app-pässwörd-123"
    ) = session.signIn(server(url), Credentials(user, password))

    private suspend fun exportFrom(old: Phone): ByteArray =
        (old.service.export(passphrase) as BackupResult.Success).value

    private fun failure(result: BackupResult<*>) = (result as BackupResult.Failure).error

    @Test
    fun `settings and account travel to a fresh install`() = runTest {
        val old = Phone(codec)
        old.signIn()
        old.settings.setTheme(ThemeMode.DARK)
        old.settings.setAmoled(true)
        old.settings.setDynamicColor(false)
        old.settings.setSortOrder(NoteSortOrder.TITLE)
        old.settings.setSyncInterval(SyncInterval.entries.last())
        old.settings.setSyncNetwork(SyncNetwork.UNMETERED)
        old.settings.setLockTimeout(LockTimeout.entries.last())
        old.settings.setSecureWindow(true)
        val file = exportFrom(old)
        val fresh = Phone(codec)

        val result = fresh.service.restore(file, passphrase)

        assertEquals(BackupResult.Success(Unit), result)
        assertEquals(old.settings.current, fresh.settings.current)
        assertEquals(old.session.activeAccount.value, fresh.session.activeAccount.value)
        assertEquals("app-pässwörd-123", fresh.session.credentials()?.appPassword)
    }

    @Test
    fun `the restored password is stored through the keystore cipher, not in plain text`() =
        runTest {
            val old = Phone(codec)
            old.signIn(password = "sup3r-s3cret")
            val fresh = Phone(codec)

            fresh.service.restore(exportFrom(old), passphrase)

            val stored = fresh.storage.stored!!
            assertEquals("ana", stored.username)
            assertFalse(stored.secret.ciphertext.toString(Charsets.UTF_8).contains("sup3r-s3cret"))
        }

    @Test
    fun `the app lock switch is not carried to another device`() = runTest {
        val old = Phone(codec)
        old.signIn()
        old.settings.setAppLockEnabled(true)
        val fresh = Phone(codec)

        fresh.service.restore(exportFrom(old), passphrase)

        assertFalse(fresh.settings.current.appLockEnabled)
    }

    @Test
    fun `restoring over the same account keeps the lock switch and updates the rest`() = runTest {
        val old = Phone(codec)
        old.signIn()
        old.settings.setTheme(ThemeMode.LIGHT)
        val file = exportFrom(old)
        val same = Phone(codec)
        same.signIn(password = "older-password")
        same.settings.setAppLockEnabled(true)

        val result = same.service.restore(file, passphrase)

        assertEquals(BackupResult.Success(Unit), result)
        assertEquals(ThemeMode.LIGHT, same.settings.current.theme)
        assertTrue(same.settings.current.appLockEnabled)
        assertEquals("app-pässwörd-123", same.session.credentials()?.appPassword)
    }

    @Test
    fun `another signed-in account blocks the restore and nothing changes`() = runTest {
        val old = Phone(codec)
        old.signIn()
        old.settings.setTheme(ThemeMode.DARK)
        val file = exportFrom(old)
        val other = Phone(codec)
        other.signIn(user = "bob")

        val result = other.service.restore(file, passphrase)

        assertEquals(BackupError.AccountMismatch, failure(result))
        assertEquals(AppSettings(), other.settings.current)
        assertEquals("bob", other.storage.stored?.username)
    }

    @Test
    fun `a different server also blocks the restore`() = runTest {
        val old = Phone(codec)
        old.signIn()
        val other = Phone(codec)
        other.signIn(url = "https://other.example.org/")

        assertEquals(
            BackupError.AccountMismatch,
            failure(other.service.restore(exportFrom(old), passphrase))
        )
    }

    @Test
    fun `a wrong passphrase restores nothing`() = runTest {
        val old = Phone(codec)
        old.signIn()
        old.settings.setTheme(ThemeMode.DARK)
        val file = exportFrom(old)
        val fresh = Phone(codec)

        val result = fresh.service.restore(file, "wrong passphrase".toCharArray())

        assertEquals(BackupError.WrongPassphraseOrCorrupt, failure(result))
        assertNull(fresh.storage.stored)
        assertTrue(fresh.preferences.values.isEmpty())
    }

    @Test
    fun `a corrupted or foreign file restores nothing`() = runTest {
        val old = Phone(codec)
        old.signIn()
        val file = exportFrom(old)
        val fresh = Phone(codec)

        val damaged = file.copyOf().also {
            it[it.size - 3] =
                (it[it.size - 3].toInt() xor 1).toByte()
        }
        assertEquals(
            BackupError.WrongPassphraseOrCorrupt,
            failure(fresh.service.restore(damaged, passphrase))
        )
        assertEquals(
            BackupError.WrongPassphraseOrCorrupt,
            failure(fresh.service.restore(file.copyOf(file.size / 2), passphrase))
        )
        assertEquals(
            BackupError.NotABackup,
            failure(fresh.service.restore("{}".toByteArray(), passphrase))
        )
        assertNull(fresh.storage.stored)
        assertTrue(fresh.preferences.values.isEmpty())
    }

    @Test
    fun `a newer format version is refused`() = runTest {
        val old = Phone(codec)
        old.signIn()
        val file = exportFrom(old).also { it[4] = 2 }
        val fresh = Phone(codec)

        assertEquals(
            BackupError.UnsupportedVersion(2),
            failure(fresh.service.restore(file, passphrase))
        )
        assertNull(fresh.storage.stored)
    }

    private suspend fun restoreCrafted(content: String): Pair<Phone, BackupResult<Unit>> {
        val fresh = Phone(codec)
        val file = codec.seal(content.toByteArray(), passphrase)
        return fresh to fresh.service.restore(file, passphrase)
    }

    private val goodSettings = """
        "settings":{"theme":"DARK","amoled":true,"dynamicColor":false,"sortOrder":"TITLE",
        "syncInterval":"HOUR","syncNetwork":"ANY","lockTimeout":"IMMEDIATELY","secureWindow":false}
    """.trimIndent().replace("\n", "")

    private val goodAccount =
        """{"serverUrl":"https://c.example/","username":"a","appPassword":"p"}"""

    @Test
    fun `authentic but malformed content is refused whole`() = runTest {
        val cases = listOf(
            "not json",
            "{}",
            """{$goodSettings}""",
            """{$goodSettings,"account":{"serverUrl":"https://c.example/","username":"a"}}""",
            // unknown enum value: no half-applied settings
            """{${goodSettings.replace(
                "DARK",
                "NEON"
            )},"account":{"serverUrl":"https://c.example/","username":"a","appPassword":"p"}}"""
        )

        for (content in cases) {
            val (fresh, result) = restoreCrafted(content)
            assertEquals(BackupError.InvalidContent, failure(result), content)
            assertNull(fresh.storage.stored)
            assertTrue(fresh.preferences.values.isEmpty())
        }
    }

    @Test
    fun `an insecure or empty account in the backup is refused`() = runTest {
        val accounts = listOf(
            """{"serverUrl":"http://c.example/","username":"a","appPassword":"p"}""",
            """{"serverUrl":"","username":"a","appPassword":"p"}""",
            """{"serverUrl":"https://c.example/","username":" ","appPassword":"p"}""",
            """{"serverUrl":"https://c.example/","username":"a","appPassword":""}"""
        )

        for (account in accounts) {
            val (fresh, result) = restoreCrafted("""{$goodSettings,"account":$account}""")
            assertEquals(BackupError.InvalidContent, failure(result), account)
            assertNull(fresh.storage.stored)
            assertTrue(fresh.preferences.values.isEmpty())
        }
    }

    @Test
    fun `unknown fields from a later minor addition are ignored`() = runTest {
        val (fresh, result) = restoreCrafted(
            """{$goodSettings,"extra":1,"account":$goodAccount}"""
        )

        assertEquals(BackupResult.Success(Unit), result)
        assertEquals(ThemeMode.DARK, fresh.settings.current.theme)
        assertEquals("a", fresh.storage.stored?.username)
    }

    @Test
    fun `a keystore failure restores no settings either`() = runTest {
        val old = Phone(codec)
        old.signIn()
        old.settings.setTheme(ThemeMode.DARK)
        val file = exportFrom(old)
        val broken = object : com.qtekfun.ultimatenotes.data.auth.SecretCipher {
            override fun encrypt(plaintext: ByteArray) = throw GeneralSecurityException("no key")

            override fun decrypt(
                secret: com.qtekfun.ultimatenotes.data.auth.EncryptedSecret
            ): ByteArray = throw GeneralSecurityException("no key")
        }
        val fresh = Phone(codec, broken)

        val result = fresh.service.restore(file, passphrase)

        assertEquals(BackupError.StorageFailed, failure(result))
        assertTrue(fresh.preferences.values.isEmpty())
        assertNull(fresh.storage.stored)
    }

    @Test
    fun `export needs a signed-in account`() = runTest {
        val phone = Phone(codec)

        assertEquals(BackupError.NotSignedIn, failure(phone.service.export(passphrase)))
    }

    @Test
    fun `export rejects empty and short passphrases`() = runTest {
        val phone = Phone(codec)
        phone.signIn()

        for (weak in listOf("", "1234567")) {
            assertEquals(
                BackupError.WeakPassphrase,
                failure(phone.service.export(weak.toCharArray()))
            )
        }
        assertTrue(phone.service.export("12345678".toCharArray()) is BackupResult.Success)
    }

    @Test
    fun `very long values and unicode survive`() = runTest {
        val longName = "ü".repeat(2_000)
        val longPassword = "pässwörd-日本語-".repeat(2_000)
        val old = Phone(codec)
        old.signIn(
            url = "https://cloud.example.com/" + "a/".repeat(100),
            user = longName,
            password = longPassword
        )
        val fresh = Phone(codec)

        val unicodePassphrase = "contraseña-🔐-日本".toCharArray()
        val file = (old.service.export(unicodePassphrase) as BackupResult.Success).value
        assertEquals(BackupResult.Success(Unit), fresh.service.restore(file, unicodePassphrase))
        assertEquals(longName, fresh.session.credentials()?.username)
        assertEquals(longPassword, fresh.session.credentials()?.appPassword)
    }

    @Test
    fun `the account description never shows the password`() {
        val text = BackupAccount("https://c.example/", "ana", "hunter2-secret").toString()

        assertFalse(text.contains("hunter2-secret"))
        assertTrue(text.contains("ana"))
    }
}
