// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.api

import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Builds a [NotesClient] for a server. */
class NotesClientFactory @Inject constructor(
    private val credentials: CredentialsProvider,
    private val io: CoroutineDispatcher
) {
    /** [serverUrl] is the Nextcloud root, e.g. `https://cloud.example.org` or `.../nextcloud`. */
    fun create(serverUrl: String, httpClient: OkHttpClient = OkHttpClient()): NotesClient {
        val baseUrl = baseUrl(serverUrl)
        val client = httpClient.newBuilder()
            .addInterceptor(BasicAuthInterceptor(credentials, baseUrl.toHttpUrl().host))
            // Never follow an https -> http redirect: it would downgrade the connection.
            .followSslRedirects(false)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        return NotesClient(retrofit.create(NotesApi::class.java), io, json)
    }

    companion object {
        const val API_PATH = "index.php/apps/notes/api/v1/"

        val json: Json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        fun baseUrl(serverUrl: String): String =
            serverUrl.trim().trimEnd('/').plus('/').plus(API_PATH).toHttpUrl().toString()
    }
}
