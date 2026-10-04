// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync

import com.qtekfun.ultimatenotes.data.api.NoteDto
import com.qtekfun.ultimatenotes.data.api.NoteWriteDto
import com.qtekfun.ultimatenotes.data.api.NotesApi
import com.qtekfun.ultimatenotes.data.api.NotesClientFactory
import com.qtekfun.ultimatenotes.data.api.SettingsDto
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

/**
 * A faithful in-memory Nextcloud Notes server, speaking the same [NotesApi] the app uses so the
 * real [com.qtekfun.ultimatenotes.data.api.NotesClient] runs against it. Faithful means:
 *
 * - a note's etag changes if and only if the note changes, and `If-Match` is compared against the
 *   etag wrapped in double quotes, as the real server does (a missing header skips the check);
 * - 412 carries the server's current note, 404 for unknown ids, ids are never reused;
 * - the list has its own ETag (304 on a match), `Last-Modified`, `pruneBefore` (notes modified
 *   strictly before it come as bare ids) and chunking, where the bare ids of pruned notes come
 *   only in the last chunk;
 * - every write moves the server clock one second forward;
 * - the title is its own field (API >= 1.0): POST without a title stores "New note", PUT without
 *   one leaves it alone, and a stored title is sanitized like `NoteUtil::getSafeTitle` does
 *   (illegal characters removed, at most 100 characters) and numbered ("x (2)") when another note
 *   of the same category has it. The content never renames a note.
 */
class FakeNotesServer : NotesApi {
    private data class Stored(
        val id: Long,
        val title: String,
        val content: String,
        val category: String,
        val favorite: Boolean,
        val modified: Long,
        val version: Int
    ) {
        val etag get() = "e$id-$version"

        fun dto() = NoteDto(
            id = id,
            etag = etag,
            modified = modified,
            title = title,
            category = category,
            content = content,
            favorite = favorite
        )
    }

    /** How a request is made to fail. */
    sealed interface Fault {
        data object Offline : Fault

        data class Status(val code: Int) : Fault
    }

    private val notes = sortedMapOf<Long, Stored>()
    private var nextId = 1L
    private var clock = START

    /** One entry per request, e.g. `GET`, `POST`, `PUT 3`, `DELETE 3`. */
    val requests = mutableListOf<String>()

    /** Query and headers of every list request, in order. */
    val listCalls = mutableListOf<ListCall>()

    data class ListCall(
        val ifNoneMatch: String?,
        val pruneBefore: Long?,
        val chunkSize: Int?,
        val chunkCursor: String?
    )

    /** Called with the request name before it is handled (to inject local edits, delays...). */
    var onRequest: suspend (String) -> Unit = {}

    /** Decides whether a request fails; checked after [onRequest]. */
    var fault: (String) -> Fault? = { null }

    /** Misbehaves like an old server: every listed note comes as a bare id. */
    var alwaysPrune = false

    val size get() = notes.size

    /** Titles of the notes, in id order. */
    fun titles(): List<String> = notes.values.map { it.title }

    fun titleOf(id: Long): String = notes.getValue(id).title

    /** Current notes as (content, category, favorite), for assertions. */
    fun snapshot(): List<Triple<String, String, Boolean>> =
        notes.values.map { Triple(it.content, it.category, it.favorite) }

    fun contents(): List<String> = notes.values.map { it.content }

    fun ids(): List<Long> = notes.keys.toList()

    /**
     * A note created by another client, straight on the server. Like the web UI, it is titled
     * after its first line unless [title] says otherwise.
     */
    fun put(
        content: String,
        category: String = "",
        favorite: Boolean = false,
        title: String = content.lineSequence().firstOrNull().orEmpty()
    ): Long {
        val id = nextId++
        val unique = uniqueTitle(id, category, title)
        notes[id] = Stored(id, unique, content, category, favorite, ++clock, 1)
        return id
    }

    fun edit(id: Long, content: String) = change(id) { it.copy(content = content) }

    /** The title changed by another client (the web UI's rename). */
    fun retitle(id: Long, title: String) =
        change(id) { it.copy(title = uniqueTitle(id, it.category, title)) }

    private fun uniqueTitle(id: Long, category: String, wanted: String): String {
        val base = wanted.replace(ILLEGAL, "").replace(WHITESPACE, " ").trim().take(MAX_TITLE)
            .ifEmpty { NEW_NOTE }
        var title = base
        var n = 1
        while (notes.values.any { it.id != id && it.category == category && it.title == title }) {
            title = "$base (${++n})"
        }
        return title
    }

    fun remove(id: Long) {
        notes.remove(id)
    }

    fun etagOf(id: Long): String = notes.getValue(id).etag

    private fun change(id: Long, transform: (Stored) -> Stored) {
        val old = notes.getValue(id)
        val new = transform(old)
        if (new != old) notes[id] = new.copy(version = old.version + 1, modified = ++clock)
    }

    private suspend fun enter(request: String): Fault? {
        requests += request
        onRequest(request)
        return fault(request)
    }

    private fun <T> Fault.toResponse(): Response<T> = when (this) {
        Fault.Offline -> throw IOException("offline")
        is Fault.Status -> Response.error(code, EMPTY)
    }

    private fun listEtag() = "\"L" + notes.values.joinToString(",") { it.etag }.hashCode() + "\""

    override suspend fun listNotes(
        ifNoneMatch: String?,
        pruneBefore: Long?,
        chunkSize: Int?,
        chunkCursor: String?
    ): Response<List<NoteDto>> {
        listCalls += ListCall(ifNoneMatch, pruneBefore, chunkSize, chunkCursor)
        return enter("GET")?.toResponse() ?: list(ifNoneMatch, pruneBefore, chunkSize, chunkCursor)
    }

    private fun list(
        ifNoneMatch: String?,
        pruneBefore: Long?,
        chunkSize: Int?,
        chunkCursor: String?
    ): Response<List<NoteDto>> {
        val etag = listEtag()
        if (ifNoneMatch == etag) {
            val raw = okhttp3.Response.Builder()
                .code(NOT_MODIFIED)
                .message("Not Modified")
                .protocol(Protocol.HTTP_1_1)
                .request(Request.Builder().url("http://localhost/").build())
                .build()
            return Response.error(EMPTY, raw)
        }
        val all = notes.values.toList()
        val pruned = all.filter {
            alwaysPrune || (pruneBefore != null && it.modified < pruneBefore)
        }
        val full = all - pruned.toSet()
        val after = chunkCursor?.toLong() ?: 0L
        val remaining = full.filter { it.id > after }
        val chunk = if (chunkSize == null) remaining else remaining.take(chunkSize)
        val last = chunkSize == null || remaining.size <= chunkSize
        val body =
            chunk.map { it.dto() } + if (last) pruned.map { NoteDto(id = it.id) } else emptyList()
        val headers = Headers.Builder()
            .add("ETag", etag)
            .add("Last-Modified", httpDate(all.maxOfOrNull { it.modified } ?: clock))
        if (!last) headers.add("X-Notes-Chunk-Cursor", chunk.last().id.toString())
        return Response.success(body, headers.build())
    }

    override suspend fun createNote(note: NoteWriteDto): Response<NoteDto> =
        enter("POST")?.toResponse() ?: run {
            val id = put(
                note.content.orEmpty(),
                note.category.orEmpty(),
                note.favorite ?: false,
                note.title.orEmpty()
            )
            Response.success(notes.getValue(id).dto())
        }

    override suspend fun updateNote(
        id: Long,
        ifMatch: String?,
        note: NoteWriteDto
    ): Response<NoteDto> = enter("PUT $id")?.toResponse() ?: update(id, ifMatch, note)

    private fun update(id: Long, ifMatch: String?, note: NoteWriteDto): Response<NoteDto> {
        val current = notes[id]
        return when {
            current == null -> Response.error(HTTP_NOT_FOUND, EMPTY)

            !ifMatch.isNullOrEmpty() && ifMatch != "\"${current.etag}\"" -> {
                val body = NotesClientFactory.json.encodeToString(
                    NoteDto.serializer(),
                    current.dto()
                )
                Response.error(HTTP_PRECONDITION_FAILED, body.toResponseBody(JSON))
            }

            else -> {
                change(id) {
                    val category = note.category ?: it.category
                    it.copy(
                        title = note.title?.takeIf { t -> t != it.title }
                            ?.let { t -> uniqueTitle(id, category, t) } ?: it.title,
                        content = note.content ?: it.content,
                        category = category,
                        favorite = note.favorite ?: it.favorite
                    )
                }
                Response.success(notes.getValue(id).dto())
            }
        }
    }

    override suspend fun deleteNote(id: Long): Response<Unit> = enter("DELETE $id")?.toResponse()
        ?: if (notes.remove(id) ==
            null
        ) {
            Response.error(HTTP_NOT_FOUND, EMPTY)
        } else {
            Response.success(Unit)
        }

    override suspend fun settings(): Response<SettingsDto> = Response.success(SettingsDto())

    private fun httpDate(seconds: Long): String =
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }.format(Date(seconds * MILLIS))

    private companion object {
        const val START = 1_700_000_000L
        const val MAX_TITLE = 100
        const val NEW_NOTE = "New note"
        val ILLEGAL = Regex("""[*|/\\:"<>?]""")
        val WHITESPACE = Regex("""\s""")
        const val MILLIS = 1000L
        const val NOT_MODIFIED = 304
        const val HTTP_NOT_FOUND = 404
        const val HTTP_PRECONDITION_FAILED = 412
        val JSON = "application/json".toMediaType()
        val EMPTY = "".toResponseBody(null)
    }
}
