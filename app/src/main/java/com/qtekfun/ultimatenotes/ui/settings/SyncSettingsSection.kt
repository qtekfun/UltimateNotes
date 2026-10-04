// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.data.settings.AppSettings
import com.qtekfun.ultimatenotes.data.settings.SyncInterval
import com.qtekfun.ultimatenotes.data.settings.SyncNetwork

@Composable
internal fun SyncSection(
    settings: AppSettings,
    onInterval: (SyncInterval) -> Unit,
    onNetwork: (SyncNetwork) -> Unit
) {
    SectionHeader(R.string.settings_sync)
    OptionGroup(R.string.settings_sync_interval) {
        SyncInterval.entries.forEach { interval ->
            OptionRow(
                label = when (interval) {
                    SyncInterval.OFF -> R.string.sync_interval_off
                    SyncInterval.QUARTER_HOUR -> R.string.sync_interval_15m
                    SyncInterval.HOUR -> R.string.sync_interval_1h
                    SyncInterval.SIX_HOURS -> R.string.sync_interval_6h
                },
                selected = settings.syncInterval == interval,
                onClick = { onInterval(interval) }
            )
        }
    }
    OptionGroup(R.string.settings_sync_network) {
        SyncNetwork.entries.forEach { network ->
            OptionRow(
                label = when (network) {
                    SyncNetwork.ANY -> R.string.sync_network_any
                    SyncNetwork.UNMETERED -> R.string.sync_network_unmetered
                },
                selected = settings.syncNetwork == network,
                onClick = { onNetwork(network) }
            )
        }
    }
}

@Composable
private fun OptionGroup(title: Int, content: @Composable () -> Unit) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
    Column(Modifier.selectableGroup()) { content() }
}
