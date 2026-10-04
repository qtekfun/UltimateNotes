// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatenotes.domain.auth.LoginFlow
import com.qtekfun.ultimatenotes.domain.auth.LoginState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the login screen shows; [step] is null before the user starts. */
data class LoginUiState(val server: String = "", val step: LoginState? = null) {
    val busy: Boolean get() = step != null && step !is LoginState.Failed
}

@HiltViewModel
class LoginViewModel @Inject constructor(private val loginFlow: LoginFlow) : ViewModel() {
    private val mutableState = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = mutableState.asStateFlow()
    private var job: Job? = null

    fun onServerChange(server: String) {
        // Editing the address clears the previous error.
        mutableState.update {
            it.copy(server = server, step = if (it.step is LoginState.Failed) null else it.step)
        }
    }

    fun logIn() {
        job?.cancel()
        job = viewModelScope.launch {
            loginFlow.login(state.value.server).collect { step ->
                mutableState.update { it.copy(step = step) }
            }
        }
    }

    /** Stops polling; the token simply expires on the server. */
    fun cancel() {
        job?.cancel()
        mutableState.update { it.copy(step = null) }
    }
}
