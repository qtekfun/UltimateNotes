// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.api

/** Outcome of a call to the Notes API: the client never throws to its callers. */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>

    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

/** Everything that can go wrong talking to the server. */
sealed interface ApiError {
    /** No connection, timeout or any other I/O failure. */
    data object Offline : ApiError

    /** 401: no or wrong credentials. */
    data object Unauthorized : ApiError

    /** 404 on a collection endpoint: the Notes app is not installed or enabled. */
    data object NotesAppMissing : ApiError

    /** 404 on a single note: it no longer exists on the server. */
    data object NotFound : ApiError

    /** 403: the note is read-only. */
    data object Forbidden : ApiError

    /** 400 on `/settings`: the installed Notes app is older than API 1.2. */
    data object UnsupportedApi : ApiError

    /** 412: the etag differs; [serverNote] is the server's current state, when it could be read. */
    data class Conflict(val serverNote: NoteDto?) : ApiError

    /** Any other unexpected HTTP status (5xx, 507 insufficient storage...). */
    data class Server(val code: Int) : ApiError

    /** A 2xx answer whose body is not the JSON the API documents. */
    data object InvalidResponse : ApiError
}
