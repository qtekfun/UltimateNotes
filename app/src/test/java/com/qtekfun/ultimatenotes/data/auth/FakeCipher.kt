// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.auth

import kotlin.random.Random

/** Reversible stand-in for the Keystore cipher: XOR with a random IV. Not secure, tests only. */
class FakeCipher : SecretCipher {
    override fun encrypt(plaintext: ByteArray): EncryptedSecret {
        val iv = Random.nextBytes(12)
        return EncryptedSecret(xor(plaintext, iv), iv)
    }

    override fun decrypt(secret: EncryptedSecret): ByteArray = xor(secret.ciphertext, secret.iv)

    private fun xor(data: ByteArray, iv: ByteArray) =
        ByteArray(data.size) { (data[it].toInt() xor iv[it % iv.size].toInt() xor 0x5A).toByte() }
}
