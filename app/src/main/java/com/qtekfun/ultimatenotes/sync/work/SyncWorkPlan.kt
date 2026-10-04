// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.sync.work

import androidx.work.NetworkType
import com.qtekfun.ultimatenotes.data.settings.AppSettings
import com.qtekfun.ultimatenotes.data.settings.SyncNetwork
import java.time.Duration

/** The periodic work to schedule: how often and on which networks. */
data class PeriodicPlan(val interval: Duration, val network: NetworkType)

/** The scheduling decisions as pure functions of the settings. */
object SyncWorkPlan {
    const val ONE_TIME_WORK = "sync-now"
    const val PERIODIC_WORK = "sync-periodic"

    /** The periodic plan, or null when the interval is off (then the work is cancelled). */
    fun periodic(settings: AppSettings): PeriodicPlan? = settings.syncInterval.period?.let {
        PeriodicPlan(
            Duration.ofMillis(it.inWholeMilliseconds),
            networkType(settings.syncNetwork)
        )
    }

    /** Background syncs obey the setting; a manual refresh is the user asking, so any network. */
    fun oneTimeNetwork(settings: AppSettings, manual: Boolean): NetworkType =
        if (manual) NetworkType.CONNECTED else networkType(settings.syncNetwork)

    private fun networkType(network: SyncNetwork): NetworkType = when (network) {
        SyncNetwork.ANY -> NetworkType.CONNECTED
        SyncNetwork.UNMETERED -> NetworkType.UNMETERED
    }
}
