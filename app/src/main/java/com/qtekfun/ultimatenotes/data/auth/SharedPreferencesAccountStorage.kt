// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.auth

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * [AccountStorage] over a private SharedPreferences file. Only the Keystore-encrypted password
 * (ciphertext and IV, Base64) is written; the file is excluded from backups (see backup rules).
 */
class SharedPreferencesAccountStorage @Inject constructor(@ApplicationContext context: Context) :
    AccountStorage {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun load(): StoredAccount? {
        val serverUrl = prefs.getString(KEY_SERVER, null)
        val username = prefs.getString(KEY_USER, null)
        val secret = decode(prefs.getString(KEY_CIPHERTEXT, null), prefs.getString(KEY_IV, null))
        return if (serverUrl != null && username != null && secret != null) {
            StoredAccount(serverUrl, username, secret)
        } else {
            null
        }
    }

    private fun decode(ciphertext: String?, iv: String?): EncryptedSecret? =
        if (ciphertext != null && iv != null) {
            EncryptedSecret(
                Base64.decode(ciphertext, Base64.NO_WRAP),
                Base64.decode(iv, Base64.NO_WRAP)
            )
        } else {
            null
        }

    override fun save(account: StoredAccount) = prefs.edit(commit = true) {
        putString(KEY_SERVER, account.serverUrl)
        putString(KEY_USER, account.username)
        putString(KEY_CIPHERTEXT, Base64.encodeToString(account.secret.ciphertext, Base64.NO_WRAP))
        putString(KEY_IV, Base64.encodeToString(account.secret.iv, Base64.NO_WRAP))
    }

    override fun clear() = prefs.edit(commit = true) { clear() }

    private companion object {
        const val FILE = "account"
        const val KEY_SERVER = "server_url"
        const val KEY_USER = "username"
        const val KEY_CIPHERTEXT = "password_ciphertext"
        const val KEY_IV = "password_iv"
    }
}
