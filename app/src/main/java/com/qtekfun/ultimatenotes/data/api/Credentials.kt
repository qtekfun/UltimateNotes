// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.api

/** Nextcloud user name and app password. The `toString` never reveals the password. */
data class Credentials(val username: String, val appPassword: String) {
    override fun toString(): String = "Credentials(username=$username, appPassword=***)"
}

/**
 * Source of the credentials, implemented over the Android Keystore by the login flow (T04).
 * Returns null when no account is signed in.
 */
fun interface CredentialsProvider {
    fun credentials(): Credentials?
}
