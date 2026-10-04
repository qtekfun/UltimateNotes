// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatenotes.data.settings.AppSettings
import com.qtekfun.ultimatenotes.data.settings.SettingsRepository
import com.qtekfun.ultimatenotes.domain.lock.LockCapability
import com.qtekfun.ultimatenotes.domain.lock.LockCapabilityChecker
import com.qtekfun.ultimatenotes.domain.lock.LockTimeout
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class LockSettingsState(
    val settings: AppSettings = AppSettings(),
    val capability: LockCapability = LockCapability.AVAILABLE
) {
    /** The lock can be switched on only if the device can authenticate; it can always go off. */
    val canToggleLock: Boolean
        get() = settings.appLockEnabled || capability == LockCapability.AVAILABLE
}

@HiltViewModel
class LockSettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val checker: LockCapabilityChecker
) : ViewModel() {
    private val capability = MutableStateFlow(checker.capability())

    val state: StateFlow<LockSettingsState> = combine(repository.settings, capability) { s, c ->
        LockSettingsState(s, c)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_MS),
        LockSettingsState(repository.current, capability.value)
    )

    /** The user may have set a screen lock up in the system settings meanwhile. */
    fun refreshCapability() {
        capability.value = checker.capability()
    }

    fun setLockEnabled(on: Boolean) {
        refreshCapability()
        if (!on || capability.value == LockCapability.AVAILABLE) repository.setAppLockEnabled(on)
    }

    fun setTimeout(timeout: LockTimeout) = repository.setLockTimeout(timeout)

    fun setSecureWindow(on: Boolean) = repository.setSecureWindow(on)

    private companion object {
        const val STOP_MS = 5_000L
    }
}
