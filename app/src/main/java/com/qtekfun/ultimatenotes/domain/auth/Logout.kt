// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.auth

import com.qtekfun.ultimatenotes.data.auth.AccountSession
import com.qtekfun.ultimatenotes.data.auth.LoginFlowApiFactory
import com.qtekfun.ultimatenotes.data.auth.ServerUrl
import com.qtekfun.ultimatenotes.data.auth.authCall
import com.qtekfun.ultimatenotes.data.auth.basicAuth
import javax.inject.Inject

/**
 * Logs out (SPEC §2): revokes the app password on the server, then forgets the account and its
 * stored credentials. Revocation is best effort: as Nextcloud recommends, the account is removed
 * even if the server cannot be reached.
 */
class Logout @Inject constructor(
    private val apiFactory: LoginFlowApiFactory,
    private val session: AccountSession
) {
    suspend operator fun invoke() = run(allowInsecure = false)

    /** [allowInsecure] exists only so tests can revoke against a local plain-http server. */
    internal suspend fun run(allowInsecure: Boolean) {
        val account = session.activeAccount.value ?: session.restore() ?: return
        val credentials = session.credentials()
        val server = ServerUrl.parse(
            account.serverUrl,
            allowInsecure
        ) as? ServerUrl.ParseResult.Valid
        if (credentials != null && server != null) {
            authCall { apiFactory.create(server.url).revokeAppPassword(basicAuth(credentials)) }
        }
        session.signOut()
    }
}
