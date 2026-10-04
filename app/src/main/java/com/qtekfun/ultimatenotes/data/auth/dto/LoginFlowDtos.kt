// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.auth.dto

import kotlinx.serialization.Serializable

/** `status.php`: present on every Nextcloud server, no login needed. */
@Serializable
data class StatusDto(
    val installed: Boolean = false,
    val maintenance: Boolean = false,
    val version: String? = null,
    val productname: String? = null
)

/** Response of POST index.php/login/v2. */
@Serializable
data class LoginStartDto(val poll: Poll, val login: String) {
    /** The login URL embeds the one-time token, so nothing is printed. */
    override fun toString(): String = "LoginStartDto(<redacted>)"

    @Serializable
    data class Poll(val token: String, val endpoint: String) {
        override fun toString(): String = "Poll(token=***)"
    }
}

/** Response of the poll endpoint once the user has logged in. Returned only once. */
@Serializable
data class LoginResultDto(val server: String, val loginName: String, val appPassword: String) {
    /** Never reveals the app password. */
    override fun toString(): String = "LoginResultDto(server=$server, appPassword=***)"
}
