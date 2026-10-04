// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.widget

import app.cash.turbine.test
import com.qtekfun.ultimatenotes.data.api.Credentials
import com.qtekfun.ultimatenotes.data.auth.AccountSession
import com.qtekfun.ultimatenotes.data.auth.FakeAccountStorage
import com.qtekfun.ultimatenotes.data.auth.FakeCipher
import com.qtekfun.ultimatenotes.data.auth.ServerUrl
import com.qtekfun.ultimatenotes.data.local.entity.NoteEntity
import com.qtekfun.ultimatenotes.data.local.inMemoryDatabase
import com.qtekfun.ultimatenotes.data.local.model.SyncState
import com.qtekfun.ultimatenotes.data.settings.FakePreferences
import com.qtekfun.ultimatenotes.data.settings.SettingsRepository
import com.qtekfun.ultimatenotes.domain.lock.AppLockState
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/** Real time on purpose: Room emits from its own threads, which virtual time cannot order. */
class WidgetChangesTest {
    private val database = inMemoryDatabase()
    private val dao = database.noteDao()
    private val lockedFlow = MutableStateFlow(false)
    private val lock = object : AppLockState {
        override val locked: StateFlow<Boolean> = lockedFlow
    }
    private val settings = SettingsRepository(FakePreferences())
    private val session = AccountSession(FakeAccountStorage(), FakeCipher(), Dispatchers.Unconfined)
    private val changes = WidgetChanges(dao, lock, settings, session, WidgetContentSelector())
    private val quiet = 300.milliseconds

    @AfterEach
    fun close() = database.close()

    private suspend fun waitForQuiet() = delay(quiet * 5)

    @Test
    fun `starting emits once and a burst of changes is merged into one refresh`() = runBlocking {
        changes.observe(quiet).test {
            awaitItem()
            dao.insert(NoteEntity(title = "A", syncState = SyncState.SYNCED, modified = 1))
            dao.insert(NoteEntity(title = "B", syncState = SyncState.SYNCED, modified = 2))
            lockedFlow.value = true
            awaitItem()
            waitForQuiet()
            expectNoEvents()
        }
    }

    @Test
    fun `a note that changes what the widget shows refreshes it`() = runBlocking {
        val id = dao.insert(NoteEntity(title = "A", syncState = SyncState.SYNCED, modified = 1))
        changes.observe(quiet).test {
            awaitItem()
            dao.update(dao.get(id)!!.copy(title = "A2"))
            awaitItem()
        }
    }

    @Test
    fun `sync bookkeeping that the widget does not show does not refresh`() = runBlocking {
        val id = dao.insert(NoteEntity(title = "A", content = "x", syncState = SyncState.SYNCED))
        changes.observe(quiet).test {
            awaitItem()
            dao.update(dao.get(id)!!.copy(etag = "new", lastSyncedEtag = "new"))
            waitForQuiet()
            expectNoEvents()
        }
    }

    @Test
    fun `a note beyond the first ten does not refresh`() = runBlocking {
        repeat(WidgetContentSelector.MAX_NOTES) {
            dao.insert(
                NoteEntity(title = "n$it", syncState = SyncState.SYNCED, modified = 1000L + it)
            )
        }
        changes.observe(quiet).test {
            awaitItem()
            dao.insert(NoteEntity(title = "old", syncState = SyncState.SYNCED, modified = 1))
            waitForQuiet()
            expectNoEvents()
        }
    }

    @Test
    fun `locking and unlocking refresh`() = runBlocking {
        changes.observe(quiet).test {
            awaitItem()
            lockedFlow.value = true
            awaitItem()
            lockedFlow.value = false
            awaitItem()
        }
    }

    @Test
    fun `switching the lock setting refreshes`() = runBlocking {
        changes.observe(quiet).test {
            awaitItem()
            settings.setAppLockEnabled(true)
            awaitItem()
        }
    }

    @Test
    fun `signing in refreshes`() = runBlocking {
        changes.observe(quiet).test {
            awaitItem()
            val server =
                (ServerUrl.parse("https://cloud.example.com") as ServerUrl.ParseResult.Valid).url
            session.signIn(server, Credentials("ana", "secret"))
            awaitItem()
        }
    }
}
