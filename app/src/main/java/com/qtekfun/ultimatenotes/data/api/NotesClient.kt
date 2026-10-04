// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.api

import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response

/** One page of the notes list. */
data class NotesPage(
    val notes: List<NoteDto>,
    /** The list's `ETag`, to send as `If-None-Match` on the next full refresh. */
    val etag: String?,
    /** `Last-Modified` as epoch seconds: the value to use as `pruneBefore` next time. */
    val lastModified: Long?,
    /** Non-null while more chunks remain: pass it as the next request's cursor. */
    val nextCursor: String?
)

/** Result of listing notes. */
sealed interface NotesListing {
    /** 304: nothing changed since the given etag. */
    data object NotModified : NotesListing

    data class Page(val page: NotesPage) : NotesListing
}

/**
 * Nextcloud Notes API v1 on top of [NotesApi]. Every call runs on [io] and returns an
 * [ApiResult]; no exception escapes. Note content is never logged.
 */
class NotesClient(
    private val api: NotesApi,
    private val io: CoroutineDispatcher,
    private val json: Json = NotesClientFactory.json
) {
    /**
     * Lists notes. Pass the previous list etag as [ifNoneMatch] for a 304. With [pruneBefore],
     * unchanged notes carry only their id. With [chunkSize], follow [NotesPage.nextCursor] until
     * it is null: deletions can only be inferred from the last chunk of a complete walk.
     */
    suspend fun listNotes(
        ifNoneMatch: String? = null,
        pruneBefore: Long? = null,
        chunkSize: Int? = null,
        chunkCursor: String? = null
    ): ApiResult<NotesListing> = call(missingIs = ApiError.NotesAppMissing) {
        api.listNotes(ifNoneMatch, pruneBefore, chunkSize, chunkCursor)
    }.map { response ->
        if (response.code() == HTTP_NOT_MODIFIED) {
            NotesListing.NotModified
        } else {
            val headers = response.headers()
            NotesListing.Page(
                NotesPage(
                    notes = response.body().orEmpty(),
                    etag = headers["ETag"],
                    lastModified = headers.getDate("Last-Modified")?.time?.div(MILLIS),
                    nextCursor = headers["X-Notes-Chunk-Cursor"]
                )
            )
        }
    }

    suspend fun createNote(note: NoteWriteDto): ApiResult<NoteDto> =
        call(missingIs = ApiError.NotesAppMissing) { api.createNote(note) }.body()

    /** Updates a note; [etag] goes in `If-Match` and a 412 comes back as [ApiError.Conflict]. */
    suspend fun updateNote(id: Long, etag: String?, note: NoteWriteDto): ApiResult<NoteDto> =
        call(missingIs = ApiError.NotFound) { api.updateNote(id, etag, note) }.body()

    suspend fun deleteNote(id: Long): ApiResult<Unit> =
        call(missingIs = ApiError.NotFound) { api.deleteNote(id) }.map { }

    suspend fun settings(): ApiResult<SettingsDto> =
        call(missingIs = ApiError.NotesAppMissing) { api.settings() }.body()

    private suspend fun <T> call(
        missingIs: ApiError,
        block: suspend () -> Response<T>
    ): ApiResult<Response<T>> = withContext(io) {
        try {
            val response = block()
            if (response.isSuccessful || response.code() == HTTP_NOT_MODIFIED) {
                ApiResult.Success(response)
            } else {
                ApiResult.Failure(errorOf(response, missingIs))
            }
        } catch (_: SerializationException) {
            ApiResult.Failure(ApiError.InvalidResponse)
        } catch (_: IOException) {
            ApiResult.Failure(ApiError.Offline)
        }
    }

    private fun errorOf(response: Response<*>, missingIs: ApiError): ApiError =
        when (val code = response.code()) {
            HTTP_UNAUTHORIZED -> ApiError.Unauthorized
            HTTP_FORBIDDEN -> ApiError.Forbidden
            HTTP_NOT_FOUND -> missingIs
            HTTP_BAD_REQUEST -> badRequest(missingIs)
            HTTP_PRECONDITION_FAILED -> ApiError.Conflict(conflictNote(response))
            else -> ApiError.Server(code)
        }

    /** Only `/settings` is told apart: there a 400 means the Notes app predates API 1.2. */
    private fun badRequest(missingIs: ApiError): ApiError = if (missingIs ==
        ApiError.NotesAppMissing
    ) {
        ApiError.UnsupportedApi
    } else {
        ApiError.Server(HTTP_BAD_REQUEST)
    }

    private fun conflictNote(response: Response<*>): NoteDto? = try {
        response.errorBody()?.string()?.let { json.decodeFromString(NoteDto.serializer(), it) }
    } catch (_: SerializationException) {
        null
    } catch (_: IOException) {
        null
    }

    private companion object {
        const val HTTP_NOT_MODIFIED = 304
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_PRECONDITION_FAILED = 412
        const val MILLIS = 1000L
    }
}

private fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}

private fun <T> ApiResult<Response<T>>.body(): ApiResult<T> = when (this) {
    is ApiResult.Success -> value.body()?.let { ApiResult.Success(it) }
        ?: ApiResult.Failure(ApiError.InvalidResponse)

    is ApiResult.Failure -> this
}
