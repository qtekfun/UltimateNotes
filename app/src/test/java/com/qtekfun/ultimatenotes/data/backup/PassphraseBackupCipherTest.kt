// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.backup

import com.qtekfun.ultimatenotes.domain.backup.BackupError
import com.qtekfun.ultimatenotes.domain.backup.BackupResult
import java.nio.ByteBuffer
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PassphraseBackupCipherTest {
    private val cipher = PassphraseBackupCipher(iterations = 1_000)
    private val plain = """{"secret":"app-password"}""".toByteArray()
    private val passphrase = "correct horse".toCharArray()

    private fun opened(file: ByteArray, with: CharArray = passphrase) = cipher.open(file, with)

    private fun assertFailure(error: BackupError, result: BackupResult<ByteArray>) =
        assertEquals(BackupResult.Failure(error), result)

    private fun assertCorrupt(result: BackupResult<ByteArray>) =
        assertFailure(BackupError.WrongPassphraseOrCorrupt, result)

    @Test
    fun `a sealed file opens with the same passphrase`() {
        val result = opened(cipher.seal(plain, passphrase)) as BackupResult.Success

        assertArrayEquals(plain, result.value)
    }

    @Test
    fun `the file starts with the magic and version and hides the content`() {
        val file = cipher.seal(plain, passphrase)

        assertEquals("UNBK", file.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals(1, file[4].toInt())
        assertFalse(file.toString(Charsets.ISO_8859_1).contains("app-password"))
    }

    @Test
    fun `the work factor is stored and the default is high enough`() {
        val file = PassphraseBackupCipher().seal(plain, passphrase)

        assertTrue(ByteBuffer.wrap(file).getInt(5) >= 600_000)
        assertTrue(opened(file) is BackupResult.Success)
    }

    @Test
    fun `every seal uses a fresh salt and IV`() {
        val first = cipher.seal(plain, passphrase)
        val second = cipher.seal(plain, passphrase)

        assertNotEquals(first.toList().subList(9, 37), second.toList().subList(9, 37))
        assertFalse(first.contentEquals(second))
    }

    @Test
    fun `a wrong passphrase is refused`() {
        val file = cipher.seal(plain, passphrase)

        assertCorrupt(opened(file, "correct horsE".toCharArray()))
        assertCorrupt(opened(file, CharArray(0)))
    }

    @Test
    fun `an empty passphrase cannot seal`() {
        assertThrows(IllegalArgumentException::class.java) { cipher.seal(plain, CharArray(0)) }
    }

    @Test
    fun `unicode passphrases and content round trip, whatever the normalisation form`() {
        val composed = "contraseña-é-日本語-🔐".toCharArray()
        val decomposed = java.text.Normalizer
            .normalize(String(composed), java.text.Normalizer.Form.NFD)
            .toCharArray()
        val content = "Ünïcode 日本語 🔐 \n\t\"quotes\"".toByteArray()
        val file = cipher.seal(content, composed)

        assertFalse(composed.contentEquals(decomposed))
        assertArrayEquals(content, (opened(file, composed) as BackupResult.Success).value)
        assertArrayEquals(content, (opened(file, decomposed) as BackupResult.Success).value)
    }

    @Test
    fun `large content round trips`() {
        val big = Random(7).nextBytes(512 * 1024)

        val result = opened(cipher.seal(big, passphrase)) as BackupResult.Success

        assertArrayEquals(big, result.value)
    }

    @Test
    fun `empty content round trips`() {
        val result = opened(cipher.seal(ByteArray(0), passphrase)) as BackupResult.Success

        assertEquals(0, result.value.size)
    }

    @Test
    fun `flipping any single byte makes the file fail`() {
        val file = cipher.seal(plain, passphrase)

        for (index in file.indices) {
            val damaged = file.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            val result = opened(damaged)
            assertTrue(result is BackupResult.Failure, "byte $index was accepted")
        }
    }

    @Test
    fun `a truncated file fails whatever its length`() {
        val file = cipher.seal(plain, passphrase)

        for (length in 0 until file.size) {
            val result = opened(file.copyOf(length))
            assertTrue(result is BackupResult.Failure, "length $length was accepted")
        }
    }

    @Test
    fun `a file cut after the header is damaged, not foreign`() {
        val file = cipher.seal(plain, passphrase)

        assertCorrupt(opened(file.copyOf(37)))
        assertCorrupt(opened(file.copyOf(file.size - 1)))
    }

    @Test
    fun `appended bytes fail`() {
        assertCorrupt(opened(cipher.seal(plain, passphrase) + byteArrayOf(0)))
    }

    @Test
    fun `a file that is not a backup is recognised as such`() {
        assertFailure(BackupError.NotABackup, opened(ByteArray(0)))
        assertFailure(BackupError.NotABackup, opened("UNB".toByteArray()))
        assertFailure(BackupError.NotABackup, opened("{\"format\":1}".toByteArray()))
        assertFailure(BackupError.NotABackup, opened(ByteArray(100) { 0 }))
    }

    @Test
    fun `another version is refused with that version, even if it decrypts`() {
        val file = cipher.seal(plain, passphrase)

        for (version in listOf(0, 2, 200, 255)) {
            val other = file.copyOf().also { it[4] = version.toByte() }
            assertFailure(BackupError.UnsupportedVersion(version), opened(other))
        }
    }

    @Test
    fun `an absurd work factor is refused without deriving a key`() {
        val file = cipher.seal(plain, passphrase)
        for (rounds in listOf(0, 999, 5_000_001, Int.MAX_VALUE, -1)) {
            val crafted = file.copyOf().also { ByteBuffer.wrap(it).putInt(5, rounds) }
            assertCorrupt(opened(crafted))
        }
    }

    @Test
    fun `a file bigger than the limit is refused`() {
        val file = cipher.seal(ByteArray(PassphraseBackupCipher.MAX_FILE_BYTES), passphrase)

        assertCorrupt(opened(file))
    }
}
