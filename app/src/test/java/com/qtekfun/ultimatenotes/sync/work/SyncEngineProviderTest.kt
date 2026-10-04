// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.work

import com.qtekfun.ultimatenotes.data.api.ApiError
import com.qtekfun.ultimatenotes.data.api.NotesClientFactory
import com.qtekfun.ultimatenotes.data.auth.AccountSession
import com.qtekfun.ultimatenotes.data.local.inMemoryDatabase
import com.qtekfun.ultimatenotes.sync.InMemoryCheckpointStore
import com.qtekfun.ultimatenotes.sync.TEST_CLOCK
import com.qtekfun.ultimatenotes.sync.queue.SyncResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncEngineProviderTest {
    private val database = inMemoryDatabase()
    private val session = mockk<AccountSession>()
    private val provider = SyncEngineProvider(
        session,
        NotesClientFactory(mockk(), Dispatchers.Unconfined),
        OkHttpClient(),
        database.noteSyncDao(),
        database.noteSyncWriteDao(),
        InMemoryCheckpointStore(),
        TEST_CLOCK,
        Dispatchers.Unconfined
    )

    @Test
    fun `without an account the run fails as unauthorized`() = runBlocking {
        every { session.activeAccount } returns MutableStateFlow(null)
        coEvery { session.restore() } returns null

        val result = provider.sync()

        assertTrue(result is SyncResult.Failed)
        assertEquals(ApiError.Unauthorized, (result as SyncResult.Failed).error)
    }
}
