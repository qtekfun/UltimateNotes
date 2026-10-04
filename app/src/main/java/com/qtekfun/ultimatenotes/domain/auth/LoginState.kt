// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.auth

/** Steps of the login (SPEC §2), as shown to the user. */
sealed interface LoginState {
    data object CheckingServer : LoginState

    /** The user must log in at [loginUrl] in the browser; the app polls meanwhile. */
    data class WaitingForBrowser(val loginUrl: String) : LoginState

    data object LoggedIn : LoginState

    data class Failed(val error: LoginError) : LoginState
}

/** Why a login failed, each with its own clear message in the UI. */
enum class LoginError {
    INVALID_URL,
    INSECURE_URL,
    NOT_NEXTCLOUD,
    UNREACHABLE,
    TLS_ERROR,
    EXPIRED,
    NOTES_APP_MISSING,
    UNSUPPORTED_API,
    UNKNOWN
}
