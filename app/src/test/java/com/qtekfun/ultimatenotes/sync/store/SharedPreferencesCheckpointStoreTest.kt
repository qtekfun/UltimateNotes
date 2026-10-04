// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.store

import android.content.SharedPreferences
import com.qtekfun.ultimatenotes.sync.queue.SyncCheckpoint
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SharedPreferencesCheckpointStoreTest {
    private val data = mutableMapOf<String, Any>()
    private val prefs = mockk<SharedPreferences>()
    private val store = SharedPreferencesCheckpointStore(prefs)

    init {
        every { prefs.getString(any(), null) } answers { data[firstArg()] as String? }
        every { prefs.contains(any()) } answers { firstArg<String>() in data }
        every { prefs.getLong(any(), any()) } answers { data.getValue(firstArg()) as Long }
        every { prefs.edit() } answers { editor() }
    }

    private fun editor(): SharedPreferences.Editor {
        val editor = mockk<SharedPreferences.Editor>()
        every { editor.remove(any()) } answers {
            data.remove(firstArg<String>())
            editor
        }
        every { editor.putString(any(), any()) } answers {
            data[firstArg()] = secondArg<String>()
            editor
        }
        every { editor.putLong(any(), any()) } answers {
            data[firstArg()] = secondArg<Long>()
            editor
        }
        every { editor.apply() } returns Unit
        return editor
    }

    @Test
    fun `nothing stored loads as no checkpoint`() = runBlocking {
        assertEquals(SyncCheckpoint.NONE, store.load())
    }

    @Test
    fun `a checkpoint round-trips`() = runBlocking {
        store.save(SyncCheckpoint("\"etag\"", 1_700_000_000))

        assertEquals(SyncCheckpoint("\"etag\"", 1_700_000_000), store.load())
    }

    @Test
    fun `saving a partial checkpoint drops what it lacks`() = runBlocking {
        store.save(SyncCheckpoint("etag", 5))
        store.save(SyncCheckpoint(null, null))

        assertEquals(SyncCheckpoint.NONE, store.load())
    }

    @Test
    fun `an epoch of zero is a real value, not absence`() = runBlocking {
        store.save(SyncCheckpoint(null, 0))

        assertEquals(SyncCheckpoint(null, 0), store.load())
    }

    @Test
    fun `clear forgets everything`() = runBlocking {
        store.save(SyncCheckpoint("etag", 5))

        store.clear()

        assertEquals(SyncCheckpoint.NONE, store.load())
    }
}
