// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.api

import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NotesClientTest {
    private lateinit var server: MockWebServer
    private var credentials: Credentials? = Credentials("alice", "app-pass")
    private lateinit var client: NotesClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val factory = NotesClientFactory({ credentials }, Dispatchers.Unconfined)
        client = factory.create(server.url("/nextcloud").toString())
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun reply(code: Int, body: String = "", vararg headers: Pair<String, String>) {
        val builder = MockResponse.Builder().code(code).body(body)
        headers.forEach { (name, value) -> builder.addHeader(name, value) }
        server.enqueue(builder.build())
    }

    private fun failure(result: ApiResult<*>): ApiError = (result as ApiResult.Failure).error

    private fun <T> success(result: ApiResult<T>): T = (result as ApiResult.Success).value

    @Test
    fun `list sends auth, validators and query parameters under the API path`() = runTest {
        reply(200, "[]", "ETag" to "\"abc\"")

        client.listNotes(
            ifNoneMatch = "\"old\"",
            pruneBefore = 1700,
            chunkSize = 50,
            chunkCursor = "cur"
        )

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals(
            "/nextcloud/index.php/apps/notes/api/v1/notes?pruneBefore=1700&chunkSize=50&chunkCursor=cur",
            request.target
        )
        assertEquals("\"old\"", request.headers["If-None-Match"])
        val expected = Base64.getEncoder().encodeToString("alice:app-pass".toByteArray())
        assertEquals("Basic $expected", request.headers["Authorization"])
    }

    @Test
    fun `list 200 parses notes and headers`() = runTest {
        reply(
            200,
            """[{"id":76,"etag":"e1","readonly":false,"modified":1376753464,"title":"T",
                "category":"a/b","content":"hi","favorite":true,"unknown":1}]""",
            "ETag" to "\"list\"",
            "Last-Modified" to "Tue, 14 Nov 2023 22:13:20 GMT",
            "X-Notes-Chunk-Cursor" to "next"
        )

        val page = (success(client.listNotes()) as NotesListing.Page).page

        assertEquals("\"list\"", page.etag)
        assertEquals(1_700_000_000L, page.lastModified)
        assertEquals("next", page.nextCursor)
        assertEquals(
            NoteDto(76, "e1", false, 1376753464, "T", "a/b", "hi", true),
            page.notes.single()
        )
    }

    @Test
    fun `list 200 without optional headers or fields`() = runTest {
        reply(200, """[{"id":5}]""")

        val page = (success(client.listNotes(pruneBefore = 1)) as NotesListing.Page).page

        assertNull(page.etag)
        assertNull(page.lastModified)
        assertNull(page.nextCursor)
        assertEquals(NoteDto(id = 5), page.notes.single())
        assertNull(page.notes.single().content)
    }

    @Test
    fun `list 304 is not modified`() = runTest {
        reply(304)

        assertEquals(NotesListing.NotModified, success(client.listNotes(ifNoneMatch = "\"x\"")))
    }

    @Test
    fun `pagination is walked with the cursor until the header disappears`() = runTest {
        reply(
            200,
            """[{"id":1,"title":"a"}]""",
            "X-Notes-Chunk-Cursor" to "c1",
            "X-Notes-Chunk-Pending" to "1"
        )
        reply(200, """[{"id":2,"title":"b"},{"id":3}]""")

        val first = (success(client.listNotes(chunkSize = 1)) as NotesListing.Page).page
        val secondResult = client.listNotes(chunkSize = 1, chunkCursor = first.nextCursor)
        val second = (success(secondResult) as NotesListing.Page).page

        assertEquals("c1", first.nextCursor)
        assertNull(second.nextCursor)
        assertEquals(listOf(2L, 3L), second.notes.map { it.id })
        server.takeRequest()
        assertTrue(server.takeRequest().target.endsWith("chunkSize=1&chunkCursor=c1"))
    }

    @Test
    fun `create posts only the given fields`() = runTest {
        reply(200, """{"id":9,"etag":"e","title":"New","category":"c","content":"body"}""")

        val note =
            success(
                client.createNote(NoteWriteDto(title = "New", category = "c", content = "body"))
            )

        assertEquals(9L, note.id)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("""{"title":"New","category":"c","content":"body"}""", request.body?.utf8())
    }

    @Test
    fun `update sends If-Match and returns the note`() = runTest {
        reply(200, """{"id":9,"etag":"e2","content":"x","favorite":true}""")

        val note = success(client.updateNote(9, "e1", NoteWriteDto(content = "x", favorite = true)))

        assertEquals("e2", note.etag)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/nextcloud/index.php/apps/notes/api/v1/notes/9", request.target)
        assertEquals("e1", request.headers["If-Match"])
    }

    @Test
    fun `update 412 is a conflict carrying the server note`() = runTest {
        reply(412, """{"id":9,"etag":"srv","content":"server"}""")

        val error = failure(client.updateNote(9, "stale", NoteWriteDto(content = "mine")))

        assertEquals(ApiError.Conflict(NoteDto(id = 9, etag = "srv", content = "server")), error)
    }

    @Test
    fun `update 412 with an unreadable body is a conflict without note`() = runTest {
        reply(412, "not json")

        assertEquals(
            ApiError.Conflict(null),
            failure(client.updateNote(9, "stale", NoteWriteDto()))
        )
    }

    @Test
    fun `update 412 with an empty body is a conflict without note`() = runTest {
        reply(412)

        assertEquals(
            ApiError.Conflict(null),
            failure(client.updateNote(9, "stale", NoteWriteDto()))
        )
    }

    @Test
    fun `update 403 is forbidden and 404 is not found`() = runTest {
        reply(403)
        reply(404)

        assertEquals(ApiError.Forbidden, failure(client.updateNote(1, null, NoteWriteDto())))
        assertEquals(ApiError.NotFound, failure(client.updateNote(1, null, NoteWriteDto())))
        assertNull(server.takeRequest().headers["If-Match"])
    }

    @Test
    fun `delete succeeds on 200 and maps 404 and 403`() = runTest {
        reply(200)
        reply(404)
        reply(403)

        assertEquals(ApiResult.Success(Unit), client.deleteNote(3))
        assertEquals("DELETE", server.takeRequest().method)
        assertEquals(ApiError.NotFound, failure(client.deleteNote(3)))
        assertEquals(ApiError.Forbidden, failure(client.deleteNote(3)))
    }

    @Test
    fun `settings parses the response`() = runTest {
        reply(200, """{"notesPath":"Notes","fileSuffix":".md"}""")

        assertEquals(SettingsDto("Notes", ".md"), success(client.settings()))
    }

    @Test
    fun `settings 400 means the API is too old and 404 means no Notes app`() = runTest {
        reply(400)
        reply(404)

        assertEquals(ApiError.UnsupportedApi, failure(client.settings()))
        assertEquals(ApiError.NotesAppMissing, failure(client.settings()))
    }

    @Test
    fun `401 is unauthorized`() = runTest {
        reply(401)

        assertEquals(ApiError.Unauthorized, failure(client.listNotes()))
    }

    @Test
    fun `404 on the collection means the Notes app is missing`() = runTest {
        reply(404)
        reply(404)

        assertEquals(ApiError.NotesAppMissing, failure(client.listNotes()))
        assertEquals(ApiError.NotesAppMissing, failure(client.createNote(NoteWriteDto())))
    }

    @Test
    fun `5xx and 507 are server errors with their code`() = runTest {
        reply(500)
        reply(507)
        reply(400)

        assertEquals(ApiError.Server(500), failure(client.listNotes()))
        assertEquals(ApiError.Server(507), failure(client.createNote(NoteWriteDto())))
        assertEquals(ApiError.Server(400), failure(client.updateNote(1, null, NoteWriteDto())))
    }

    @Test
    fun `malformed JSON is an invalid response`() = runTest {
        reply(200, "{ this is not json")
        reply(200, """{"id":"not a number"}""")

        assertEquals(ApiError.InvalidResponse, failure(client.listNotes()))
        assertEquals(ApiError.InvalidResponse, failure(client.createNote(NoteWriteDto())))
    }

    @Test
    fun `an empty 200 body where a note is expected is an invalid response`() = runTest {
        reply(200, "null")

        assertEquals(ApiError.InvalidResponse, failure(client.settings()))
    }

    @Test
    fun `a dead server is offline`() = runTest {
        server.close()

        assertEquals(ApiError.Offline, failure(client.listNotes()))
    }

    @Test
    fun `without an account no request is sent and the result is unauthorized`() = runTest {
        credentials = null

        assertEquals(ApiError.Unauthorized, failure(client.listNotes()))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `the factory accepts a custom http client`() = runTest {
        val custom = NotesClientFactory({ credentials }, Dispatchers.Unconfined)
            .create(server.url("/").toString(), OkHttpClient())
        reply(200, "[]")

        assertTrue(success(custom.listNotes()) is NotesListing.Page)
        assertEquals("/index.php/apps/notes/api/v1/notes", server.takeRequest().target)
    }

    @Test
    fun `base URL tolerates trailing slashes and whitespace`() {
        assertEquals(
            "https://cloud.example.org/index.php/apps/notes/api/v1/",
            NotesClientFactory.baseUrl(" https://cloud.example.org// ")
        )
    }

    @Test
    fun `credentials never print the password`() {
        assertEquals(
            "Credentials(username=alice, appPassword=***)",
            Credentials("alice", "secret").toString()
        )
    }
}
