// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.backup

import com.qtekfun.ultimatenotes.domain.backup.BackupCipher
import com.qtekfun.ultimatenotes.domain.backup.BackupError
import com.qtekfun.ultimatenotes.domain.backup.BackupResult
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Backup file format, version 1 (all integers big-endian):
 *
 * ```
 * "UNBK" | version u8 | PBKDF2 iterations u32 | salt 16 | IV 12 | AES-256-GCM ciphertext + tag
 * ```
 *
 * The key is PBKDF2-HMAC-SHA256 over the NFC-normalised passphrase. The whole header is
 * authenticated as GCM associated data, so changing the version, iteration count, salt or IV
 * makes the file fail to open instead of opening something else. Only the JDK/Android crypto
 * APIs are used.
 */
class PassphraseBackupCipher(
    private val iterations: Int = DEFAULT_ITERATIONS,
    private val random: SecureRandom = SecureRandom()
) : BackupCipher {
    override fun seal(plain: ByteArray, passphrase: CharArray): ByteArray {
        require(passphrase.isNotEmpty()) { "empty passphrase" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES)
            .put(MAGIC)
            .put(VERSION.toByte())
            .putInt(iterations)
            .put(salt)
            .put(iv)
            .array()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            key(passphrase, salt, iterations),
            GCMParameterSpec(TAG_BITS, iv)
        )
        cipher.updateAAD(header)
        return header + cipher.doFinal(plain)
    }

    override fun open(file: ByteArray, passphrase: CharArray): BackupResult<ByteArray> {
        val failure = checkHeader(file)
        if (failure != null) return BackupResult.Failure(failure)
        val buffer = ByteBuffer.wrap(file)
        buffer.position(MAGIC.size + 1)
        val storedIterations = buffer.getInt()
        val salt = ByteArray(SALT_BYTES).also(buffer::get)
        val iv = ByteArray(IV_BYTES).also(buffer::get)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(passphrase, salt, storedIterations),
                GCMParameterSpec(TAG_BITS, iv)
            )
            cipher.updateAAD(file, 0, HEADER_BYTES)
            BackupResult.Success(cipher.doFinal(file, HEADER_BYTES, file.size - HEADER_BYTES))
        } catch (_: GeneralSecurityException) {
            BackupResult.Failure(BackupError.WrongPassphraseOrCorrupt)
        } catch (_: IllegalArgumentException) {
            // An empty passphrase cannot derive a key.
            BackupResult.Failure(BackupError.WrongPassphraseOrCorrupt)
        }
    }

    private fun checkHeader(file: ByteArray): BackupError? {
        val prefix = MAGIC.size + 1
        return when {
            file.size < prefix || !file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC) ->
                BackupError.NotABackup

            file[MAGIC.size].toInt() != VERSION ->
                BackupError.UnsupportedVersion(file[MAGIC.size].toInt() and BYTE_MASK)

            file.size < HEADER_BYTES + TAG_BYTES || file.size > MAX_FILE_BYTES ->
                BackupError.WrongPassphraseOrCorrupt

            ByteBuffer.wrap(file).getInt(prefix) !in MIN_ITERATIONS..MAX_ITERATIONS ->
                BackupError.WrongPassphraseOrCorrupt

            else -> null
        }
    }

    private fun key(passphrase: CharArray, salt: ByteArray, rounds: Int): SecretKeySpec {
        val normalised = Normalizer.normalize(String(passphrase), Normalizer.Form.NFC).toCharArray()
        val spec = PBEKeySpec(normalised, salt, rounds, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
            normalised.fill('\u0000')
        }
    }

    companion object {
        /** OWASP 2023 recommendation for PBKDF2-HMAC-SHA256. */
        const val DEFAULT_ITERATIONS = 600_000

        /** Bounds accepted when reading, so a crafted file cannot make the phone spin forever. */
        const val MIN_ITERATIONS = 1_000
        const val MAX_ITERATIONS = 5_000_000

        /** Biggest backup read; real ones are a few hundred bytes. */
        const val MAX_FILE_BYTES = 1 shl 20

        const val VERSION = 1
        private val MAGIC = "UNBK".toByteArray(Charsets.US_ASCII)
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val TAG_BYTES = TAG_BITS / Byte.SIZE_BITS
        private const val KEY_BITS = 256
        private const val BYTE_MASK = 0xFF
        private const val HEADER_BYTES = 4 + 1 + 4 + SALT_BYTES + IV_BYTES
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KDF = "PBKDF2WithHmacSHA256"
    }
}
