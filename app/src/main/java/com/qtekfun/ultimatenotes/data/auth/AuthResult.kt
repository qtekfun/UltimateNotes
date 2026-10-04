// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.auth

/** Outcome of a Nextcloud API call. Network and HTTP failures are values, never exceptions. */
sealed interface AuthResult<out T> {
    data class Success<T>(val value: T) : AuthResult<T>

    /** 401: the app password is wrong or was revoked. */
    data object Unauthorized : AuthResult<Nothing>

    /** 404: the resource does not exist (or, while polling the login, not yet). */
    data object NotFound : AuthResult<Nothing>

    /** Any other 4xx or 5xx response. */
    data class HttpError(val code: Int) : AuthResult<Nothing>

    data class NetworkError(val kind: Kind) : AuthResult<Nothing> {
        enum class Kind { TIMEOUT, UNREACHABLE, TLS, OTHER }
    }

    /** The server answered something that is not the expected JSON. */
    data object ParseError : AuthResult<Nothing>
}
