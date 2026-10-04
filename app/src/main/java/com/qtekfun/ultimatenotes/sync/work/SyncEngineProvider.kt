// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.work

import com.qtekfun.ultimatenotes.data.api.ApiError
import com.qtekfun.ultimatenotes.data.api.NotesClientFactory
import com.qtekfun.ultimatenotes.data.auth.AccountSession
import com.qtekfun.ultimatenotes.data.local.dao.NoteSyncDao
import com.qtekfun.ultimatenotes.data.local.dao.NoteSyncWriteDao
import com.qtekfun.ultimatenotes.sync.conflict.ConflictResolver
import com.qtekfun.ultimatenotes.sync.queue.SyncCheckpointStore
import com.qtekfun.ultimatenotes.sync.queue.SyncEngine
import com.qtekfun.ultimatenotes.sync.queue.SyncReport
import com.qtekfun.ultimatenotes.sync.queue.SyncResult
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.OkHttpClient

/**
 * Builds the [SyncEngine] for the signed-in account and runs it. One engine per server is kept so
 * that its run lock serializes every trigger; with no account the run fails as Unauthorized.
 */
@Singleton
@Suppress("LongParameterList")
class SyncEngineProvider @Inject constructor(
    private val session: AccountSession,
    private val clients: NotesClientFactory,
    private val http: OkHttpClient,
    private val reads: NoteSyncDao,
    private val writes: NoteSyncWriteDao,
    private val checkpoints: SyncCheckpointStore,
    private val clock: Clock,
    private val io: CoroutineDispatcher
) : SyncSource {
    private var cached: Pair<String, SyncEngine>? = null

    override suspend fun sync(): SyncResult {
        val account = session.activeAccount.value ?: session.restore()
            ?: return SyncResult.Failed(ApiError.Unauthorized, SyncReport())
        return engineFor(account.serverUrl).sync()
    }

    @Synchronized
    private fun engineFor(serverUrl: String): SyncEngine {
        cached?.takeIf { it.first == serverUrl }?.let { return it.second }
        val engine = SyncEngine(
            client = clients.create(serverUrl, http),
            reads = reads,
            writes = writes,
            checkpoints = checkpoints,
            resolver = ConflictResolver(clock),
            io = io
        )
        cached = serverUrl to engine
        return engine
    }
}
