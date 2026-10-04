// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.GeneralSecurityException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Needs a device or emulator: the key lives in the real Android Keystore. */
@RunWith(AndroidJUnit4::class)
class AndroidKeystoreCipherTest {
    private val cipher = AndroidKeystoreCipher()

    @Test
    fun encryptsAndDecryptsARoundTrip() {
        val plain = "app-pässwörd-123".toByteArray()

        val secret = cipher.encrypt(plain)

        assertFalse(secret.ciphertext.contentEquals(plain))
        assertArrayEquals(plain, cipher.decrypt(secret))
    }

    @Test
    fun usesAFreshIvEveryTime() {
        val plain = "same".toByteArray()

        assertNotEquals(cipher.encrypt(plain).iv.toList(), cipher.encrypt(plain).iv.toList())
    }

    @Test(expected = GeneralSecurityException::class)
    fun rejectsTamperedCiphertext() {
        val secret = cipher.encrypt("secret".toByteArray())
        secret.ciphertext[0] = (secret.ciphertext[0].toInt() xor 1).toByte()

        cipher.decrypt(secret)
    }
}
