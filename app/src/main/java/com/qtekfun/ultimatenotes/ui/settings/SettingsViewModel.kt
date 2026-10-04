// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatenotes.data.auth.Account
import com.qtekfun.ultimatenotes.data.auth.AccountSession
import com.qtekfun.ultimatenotes.data.settings.AppSettings
import com.qtekfun.ultimatenotes.data.settings.SettingsRepository
import com.qtekfun.ultimatenotes.data.settings.SyncInterval
import com.qtekfun.ultimatenotes.data.settings.SyncNetwork
import com.qtekfun.ultimatenotes.data.settings.ThemeMode
import com.qtekfun.ultimatenotes.domain.auth.Logout
import com.qtekfun.ultimatenotes.domain.list.NoteSortOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val account: Account? = null,
    val loggingOut: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    session: AccountSession,
    private val logout: Logout
) : ViewModel() {
    private val loggingOut = MutableStateFlow(false)

    val state: StateFlow<SettingsUiState> = combine(
        repository.settings,
        session.activeAccount,
        loggingOut
    ) { settings, account, busy -> SettingsUiState(settings, account, busy) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MS), SettingsUiState())

    fun setTheme(theme: ThemeMode) = repository.setTheme(theme)

    fun setAmoled(on: Boolean) = repository.setAmoled(on)

    fun setDynamicColor(on: Boolean) = repository.setDynamicColor(on)

    fun setSortOrder(order: NoteSortOrder) = repository.setSortOrder(order)
    fun setSyncInterval(interval: SyncInterval) = repository.setSyncInterval(interval)

    fun setSyncNetwork(network: SyncNetwork) = repository.setSyncNetwork(network)

    /** Signs out; the app then routes to the login screen by itself. */
    fun logOut() {
        if (loggingOut.value) return
        loggingOut.value = true
        viewModelScope.launch {
            try {
                logout()
            } finally {
                loggingOut.update { false }
            }
        }
    }

    private companion object {
        const val STOP_MS = 5_000L
    }
}
