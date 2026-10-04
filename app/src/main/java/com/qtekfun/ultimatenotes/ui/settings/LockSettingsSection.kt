// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.domain.lock.LockCapability
import com.qtekfun.ultimatenotes.domain.lock.LockTimeout

/** The "Security" settings: app lock, its timeout and screenshot blocking (SPEC §8). */
@Composable
internal fun LockSettingsSection(viewModel: LockSettingsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshCapability() }
    LockSettingsContent(
        state = state,
        onLock = viewModel::setLockEnabled,
        onTimeout = viewModel::setTimeout,
        onSecureWindow = viewModel::setSecureWindow
    )
}

@Composable
internal fun LockSettingsContent(
    state: LockSettingsState,
    onLock: (Boolean) -> Unit,
    onTimeout: (LockTimeout) -> Unit,
    onSecureWindow: (Boolean) -> Unit
) {
    SectionHeader(R.string.settings_security)
    SwitchRow(
        title = R.string.settings_app_lock,
        hint = R.string.settings_app_lock_hint,
        checked = state.settings.appLockEnabled,
        onChange = onLock,
        enabled = state.canToggleLock
    )
    unavailableReason(state)?.let { reason ->
        Text(
            text = stringResource(reason),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }
    if (state.settings.appLockEnabled) {
        Text(
            text = stringResource(R.string.settings_lock_timeout),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Column(Modifier.selectableGroup()) {
            LockTimeout.entries.forEach { timeout ->
                OptionRow(
                    label = timeoutLabel(timeout),
                    selected = state.settings.lockTimeout == timeout,
                    onClick = { onTimeout(timeout) }
                )
            }
        }
    }
    SwitchRow(
        title = R.string.settings_secure_window,
        hint = R.string.settings_secure_window_hint,
        checked = state.settings.secureWindow,
        onChange = onSecureWindow
    )
}

internal fun unavailableReason(state: LockSettingsState): Int? = when {
    state.settings.appLockEnabled -> null
    state.capability == LockCapability.NOT_ENROLLED -> R.string.settings_app_lock_not_enrolled
    state.capability == LockCapability.UNSUPPORTED -> R.string.settings_app_lock_unsupported
    else -> null
}

internal fun timeoutLabel(timeout: LockTimeout): Int = when (timeout) {
    LockTimeout.IMMEDIATELY -> R.string.lock_timeout_immediately
    LockTimeout.ONE_MINUTE -> R.string.lock_timeout_1m
    LockTimeout.FIVE_MINUTES -> R.string.lock_timeout_5m
    LockTimeout.FIFTEEN_MINUTES -> R.string.lock_timeout_15m
}
