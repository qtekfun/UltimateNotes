// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.api

import javax.inject.Inject

/** What a server offers to this client, as found by [NotesDetector]. */
sealed interface NotesAvailability {
    /** The Notes app is installed and speaks API 1.2 or newer (the one with `/settings`). */
    data class Available(val settings: SettingsDto) : NotesAvailability

    /** The Notes app is not installed or not enabled for this user. */
    data object NotesAppMissing : NotesAvailability

    /** The installed Notes app predates the API this client needs. */
    data object UnsupportedApi : NotesAvailability

    /** The credentials were rejected. */
    data object Unauthorized : NotesAvailability

    /** The server could not be reached. */
    data object Offline : NotesAvailability

    /** Any other failure (server error, unexpected answer). */
    data object Failed : NotesAvailability
}

/** Checks that a server has a usable Notes app by reading its `/settings` (API >= 1.2). */
class NotesDetector @Inject constructor(private val factory: NotesClientFactory) {
    suspend fun detect(serverUrl: String): NotesAvailability =
        when (val result = factory.create(serverUrl).settings()) {
            is ApiResult.Success -> NotesAvailability.Available(result.value)

            is ApiResult.Failure -> when (result.error) {
                ApiError.NotesAppMissing -> NotesAvailability.NotesAppMissing
                ApiError.UnsupportedApi -> NotesAvailability.UnsupportedApi
                ApiError.Unauthorized -> NotesAvailability.Unauthorized
                ApiError.Offline -> NotesAvailability.Offline
                else -> NotesAvailability.Failed
            }
        }
}
