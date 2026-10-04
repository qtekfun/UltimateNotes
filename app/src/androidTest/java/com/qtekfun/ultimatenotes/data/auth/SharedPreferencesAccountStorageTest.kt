// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.auth

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Needs a device or emulator; each test starts and ends with an empty store. */
@RunWith(AndroidJUnit4::class)
class SharedPreferencesAccountStorageTest {
    private val storage =
        SharedPreferencesAccountStorage(ApplicationProvider.getApplicationContext())

    @Before
    @After
    fun clean() = storage.clear()

    @Test
    fun savesAndLoadsTheAccountWithItsEncryptedSecret() {
        storage.save(
            StoredAccount(
                "https://cloud.example.com/",
                "ana",
                EncryptedSecret(byteArrayOf(1, 2, 3), byteArrayOf(9, 8, 7))
            )
        )

        val loaded = storage.load()!!
        assertEquals("https://cloud.example.com/", loaded.serverUrl)
        assertEquals("ana", loaded.username)
        assertArrayEquals(byteArrayOf(1, 2, 3), loaded.secret.ciphertext)
        assertArrayEquals(byteArrayOf(9, 8, 7), loaded.secret.iv)
    }

    @Test
    fun clearForgetsTheAccount() {
        storage.save(
            StoredAccount("https://x/", "u", EncryptedSecret(byteArrayOf(1), byteArrayOf(2)))
        )

        storage.clear()

        assertNull(storage.load())
    }
}
