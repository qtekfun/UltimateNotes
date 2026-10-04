// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.qtekfun.ultimatenotes.data.api.ApiError
import com.qtekfun.ultimatenotes.sync.TEST_CLOCK
import com.qtekfun.ultimatenotes.sync.queue.SyncReport
import com.qtekfun.ultimatenotes.sync.queue.SyncResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SyncWorkerTest {
    private val context = mockk<Context>(relaxed = true).also {
        every { it.applicationContext } returns it
    }

    private fun work(result: SyncResult): ListenableWorker.Result = runBlocking {
        val runner = SyncRunner({ result }, FakeStatusStore(), TEST_CLOCK)
        TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(SyncWorkerFactory(runner))
            .build()
            .doWork()
    }

    @Test
    fun `a clean run succeeds`() {
        assertEquals(
            ListenableWorker.Result.success(),
            work(SyncResult.Success(SyncReport()))
        )
    }

    @Test
    fun `skipped notes ask WorkManager to retry`() {
        assertEquals(
            ListenableWorker.Result.retry(),
            work(SyncResult.Success(SyncReport(skipped = 2)))
        )
    }

    @Test
    fun `an error that needs the user fails without retry`() {
        assertEquals(
            ListenableWorker.Result.failure(),
            work(SyncResult.Failed(ApiError.Unauthorized, SyncReport()))
        )
    }
}
