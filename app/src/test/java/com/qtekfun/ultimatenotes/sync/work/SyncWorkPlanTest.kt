// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.work

import androidx.work.NetworkType
import androidx.work.WorkInfo
import com.qtekfun.ultimatenotes.data.settings.AppSettings
import com.qtekfun.ultimatenotes.data.settings.SyncInterval
import com.qtekfun.ultimatenotes.data.settings.SyncNetwork
import java.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncWorkPlanTest {
    @Test
    fun `the default is hourly on any network`() {
        assertEquals(
            PeriodicPlan(Duration.ofHours(1), NetworkType.CONNECTED),
            SyncWorkPlan.periodic(AppSettings())
        )
    }

    @Test
    fun `each interval maps to its period and off to no work`() {
        val periods = SyncInterval.entries.associateWith {
            SyncWorkPlan.periodic(AppSettings(syncInterval = it))?.interval
        }

        assertEquals(
            mapOf(
                SyncInterval.OFF to null,
                SyncInterval.QUARTER_HOUR to Duration.ofMinutes(15),
                SyncInterval.HOUR to Duration.ofHours(1),
                SyncInterval.SIX_HOURS to Duration.ofHours(6)
            ),
            periods
        )
        assertNull(SyncWorkPlan.periodic(AppSettings(syncInterval = SyncInterval.OFF)))
    }

    @Test
    fun `unmetered only restricts the periodic and background syncs`() {
        val settings = AppSettings(syncNetwork = SyncNetwork.UNMETERED)

        assertEquals(NetworkType.UNMETERED, SyncWorkPlan.periodic(settings)?.network)
        assertEquals(NetworkType.UNMETERED, SyncWorkPlan.oneTimeNetwork(settings, manual = false))
    }

    @Test
    fun `a manual refresh may use any connection`() {
        val settings = AppSettings(syncNetwork = SyncNetwork.UNMETERED)

        assertEquals(NetworkType.CONNECTED, SyncWorkPlan.oneTimeNetwork(settings, manual = true))
        assertEquals(
            NetworkType.CONNECTED,
            SyncWorkPlan.oneTimeNetwork(AppSettings(), manual = false)
        )
    }

    @Test
    fun `work names are distinct so the two workers never coalesce together`() {
        assertNotEquals(SyncWorkPlan.ONE_TIME_WORK, SyncWorkPlan.PERIODIC_WORK)
    }

    @Test
    fun `a caller stops waiting when the work finished or is backing off`() {
        assertTrue(SyncScheduler.isSettled(WorkInfo.State.SUCCEEDED, 0))
        assertTrue(SyncScheduler.isSettled(WorkInfo.State.FAILED, 0))
        assertTrue(SyncScheduler.isSettled(WorkInfo.State.CANCELLED, 0))
        assertTrue(SyncScheduler.isSettled(WorkInfo.State.ENQUEUED, 2))
        assertFalse(SyncScheduler.isSettled(WorkInfo.State.ENQUEUED, 0))
        assertFalse(SyncScheduler.isSettled(WorkInfo.State.RUNNING, 0))
        assertFalse(SyncScheduler.isSettled(WorkInfo.State.RUNNING, 3))
    }
}
