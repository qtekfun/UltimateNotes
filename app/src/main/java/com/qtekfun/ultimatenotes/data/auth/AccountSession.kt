// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.auth

import com.qtekfun.ultimatenotes.data.api.Credentials
import com.qtekfun.ultimatenotes.data.api.CredentialsProvider
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** The signed-in account, without its secret. */
data class Account(val serverUrl: String, val username: String)

/**
 * The signed-in account (SPEC: single account). The app password is stored encrypted through
 * [SecretCipher]; decrypted credentials live only in memory and are handed to the API client
 * through [CredentialsProvider].
 */
@Singleton
class AccountSession @Inject constructor(
    private val storage: AccountStorage,
    private val cipher: SecretCipher,
    private val io: CoroutineDispatcher
) : CredentialsProvider {
    private val account = MutableStateFlow<Account?>(null)
    val activeAccount: StateFlow<Account?> = account.asStateFlow()

    @Volatile
    private var current: Credentials? = null

    /** Loads the stored account, e.g. when the app starts. Null if none (or its key is gone). */
    suspend fun restore(): Account? = withContext(io) { load() }

    /** Stores the account with its encrypted app password and makes it the active one. */
    suspend fun signIn(server: ServerUrl, credentials: Credentials) = withContext(io) {
        val secret = cipher.encrypt(credentials.appPassword.toByteArray(Charsets.UTF_8))
        val serverUrl = server.root.toString()
        storage.save(StoredAccount(serverUrl, credentials.username, secret))
        current = credentials
        account.value = Account(serverUrl, credentials.username)
    }

    /** Forgets the account and deletes its stored credentials. */
    suspend fun signOut() = withContext(io) {
        storage.clear()
        current = null
        account.value = null
    }

    /**
     * The decrypted credentials, or null when signed out. Called from the network thread, so a
     * cold start decrypts lazily here.
     */
    override fun credentials(): Credentials? = current ?: load()?.let { current }

    @Synchronized
    private fun load(): Account? {
        if (current == null) {
            current = storage.load()?.let(::decrypt)
        }
        return current?.let { account.value }
    }

    /** Decrypts a stored account; null (and the account is dropped) if the Keystore key is gone. */
    private fun decrypt(stored: StoredAccount): Credentials? = try {
        val password = cipher.decrypt(stored.secret).toString(Charsets.UTF_8)
        account.value = Account(stored.serverUrl, stored.username)
        Credentials(stored.username, password)
    } catch (_: GeneralSecurityException) {
        // E.g. lock screen reset: the account must sign in again.
        storage.clear()
        null
    }
}
