// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.auth

/** The signed-in account as persisted: the app password is only ever stored encrypted. */
class StoredAccount(val serverUrl: String, val username: String, val secret: EncryptedSecret)

/** Where the single account lives between app starts. */
interface AccountStorage {
    fun load(): StoredAccount?

    fun save(account: StoredAccount)

    fun clear()
}
