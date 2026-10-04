// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.auth

/** In-memory [AccountStorage] for unit tests. */
class FakeAccountStorage : AccountStorage {
    var stored: StoredAccount? = null

    override fun load(): StoredAccount? = stored

    override fun save(account: StoredAccount) {
        stored = account
    }

    override fun clear() {
        stored = null
    }
}
