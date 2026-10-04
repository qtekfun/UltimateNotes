// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.widget

import com.qtekfun.ultimatenotes.data.api.Credentials
import com.qtekfun.ultimatenotes.data.auth.AccountSession
import com.qtekfun.ultimatenotes.data.auth.FakeAccountStorage
import com.qtekfun.ultimatenotes.data.auth.FakeCipher
import com.qtekfun.ultimatenotes.data.auth.ServerUrl
import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.inMemoryDatabase
import com.qtekfun.ultimatenotes.data.local.model.SyncState
import com.qtekfun.ultimatenotes.domain.lock.AppLockState
import com.qtekfun.ultimatenotes.domain.lock.FakeConfigSource
import com.qtekfun.ultimatenotes.domain.lock.LockConfig
import io.mockk.coVerify
import io.mockk.spyk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoadWidgetContentTest {
    private val database = inMemoryDatabase()
    private val realDao = database.noteDao()
    private val dao = spyk(realDao)
    private val storage = FakeAccountStorage()
    private val session = AccountSession(storage, FakeCipher(), Dispatchers.Unconfined)
    private val lockedFlow = MutableStateFlow(false)
    private val lock = object : AppLockState {
        override val locked: StateFlow<Boolean> = lockedFlow
    }
    private val config = FakeConfigSource()
    private val load = LoadWidgetContent(session, lock, config, dao, WidgetContentSelector())

    @AfterEach
    fun close() = database.close()

    private suspend fun signIn() {
        val server =
            (ServerUrl.parse("https://cloud.example.com") as ServerUrl.ParseResult.Valid).url
        session.signIn(server, Credentials("ana", "secret"))
    }

    private suspend fun note(title: String, modified: Long, favorite: Boolean = false) =
        realDao.insert(
            NoteEntity(
                title = title,
                content = "# $title\nbody of $title",
                modified = modified,
                favorite = favorite,
                syncState = SyncState.SYNCED
            )
        )

    @Test
    fun `signed out shows the sign in state without reading notes`() = runTest {
        note("A", 1)
        assertEquals(WidgetContent.SignedOut, load())
        coVerify(exactly = 0) { dao.recent(any()) }
    }

    @Test
    fun `a stored account is restored before deciding`() = runTest {
        signIn()
        val coldStart = LoadWidgetContent(
            AccountSession(storage, FakeCipher(), Dispatchers.Unconfined),
            lock,
            config,
            dao,
            WidgetContentSelector()
        )
        assertEquals(WidgetContent.Empty, coldStart())
    }

    @Test
    fun `signed in without notes is empty`() = runTest {
        signIn()
        assertEquals(WidgetContent.Empty, load())
    }

    @Test
    fun `shows favorites first with the stored title and a one line preview`() = runTest {
        signIn()
        note("Old", 10)
        note("Fav", 5, favorite = true)
        note("New", 20)

        val content = load() as WidgetContent.Notes

        assertEquals(listOf("Fav", "New", "Old"), content.notes.map { it.title })
        assertEquals("body of Fav", content.notes[0].preview)
    }

    @Test
    fun `deleted notes are not shown`() = runTest {
        signIn()
        realDao.insert(NoteEntity(title = "Gone", syncState = SyncState.DELETED))
        assertEquals(WidgetContent.Empty, load())
    }

    @Test
    fun `locked shows the placeholder and never reads notes`() = runTest {
        signIn()
        note("Secret", 1)
        lockedFlow.value = true

        assertEquals(WidgetContent.Locked, load())
        coVerify(exactly = 0) { dao.recent(any()) }
    }

    @Test
    fun `an enabled lock hides the notes even when the app is unlocked`() = runTest {
        signIn()
        note("Secret", 1)
        config.config = LockConfig(enabled = true)

        assertEquals(WidgetContent.Locked, load())
        coVerify(exactly = 0) { dao.recent(any()) }
    }

    @Test
    fun `switching the lock off brings the notes back`() = runTest {
        signIn()
        note("Visible", 1)
        config.config = LockConfig(enabled = true)
        assertEquals(WidgetContent.Locked, load())
        config.config = LockConfig(enabled = false)
        assertTrue(load() is WidgetContent.Notes)
    }
}
