// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Raw Retrofit definition of the Nextcloud Notes API v1. Use [NotesClient], which never throws. */
interface NotesApi {
    @GET("notes")
    suspend fun listNotes(
        @Header("If-None-Match") ifNoneMatch: String?,
        @Query("pruneBefore") pruneBefore: Long?,
        @Query("chunkSize") chunkSize: Int?,
        @Query("chunkCursor") chunkCursor: String?
    ): Response<List<NoteDto>>

    @POST("notes")
    suspend fun createNote(@Body note: NoteWriteDto): Response<NoteDto>

    @PUT("notes/{id}")
    suspend fun updateNote(
        @Path("id") id: Long,
        @Header("If-Match") ifMatch: String?,
        @Body note: NoteWriteDto
    ): Response<NoteDto>

    @DELETE("notes/{id}")
    suspend fun deleteNote(@Path("id") id: Long): Response<Unit>

    @GET("settings")
    suspend fun settings(): Response<SettingsDto>
}
