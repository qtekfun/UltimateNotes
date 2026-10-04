// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.login

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.domain.auth.LoginError
import com.qtekfun.ultimatenotes.domain.auth.LoginState

/** Minimal login (T04): the server address, then Nextcloud Login Flow v2 in the browser. */
@Composable
fun LoginScreen(modifier: Modifier = Modifier, viewModel: LoginViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val waiting = state.step as? LoginState.WaitingForBrowser
    LaunchedEffect(waiting?.loginUrl) { waiting?.let { uriHandler.openUri(it.loginUrl) } }

    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.login_title),
                style = MaterialTheme.typography.headlineSmall
            )
            OutlinedTextField(
                value = state.server,
                onValueChange = viewModel::onServerChange,
                label = { Text(stringResource(R.string.login_server_label)) },
                placeholder = { Text(stringResource(R.string.login_server_hint)) },
                singleLine = true,
                enabled = !state.busy,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { viewModel.logIn() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp)
            )
            LoginProgress(
                step = state.step,
                onLogIn = viewModel::logIn,
                onReopen = { waiting?.let { uriHandler.openUri(it.loginUrl) } },
                onCancel = viewModel::cancel
            )
        }
    }
}

@Composable
private fun LoginProgress(
    step: LoginState?,
    onLogIn: () -> Unit,
    onReopen: () -> Unit,
    onCancel: () -> Unit
) {
    when (step) {
        null, is LoginState.Failed -> {
            if (step is LoginState.Failed) {
                Text(
                    text = stringResource(errorText(step.error)),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            Button(onClick = onLogIn) { Text(stringResource(R.string.login_button)) }
        }

        is LoginState.WaitingForBrowser -> {
            Busy(R.string.login_waiting)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onReopen) { Text(stringResource(R.string.login_reopen)) }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.login_cancel)) }
            }
        }

        LoginState.CheckingServer -> Busy(R.string.login_checking)

        is LoginState.LoggedIn -> Busy(R.string.login_signing_in)
    }
}

@Composable
private fun Busy(@StringRes text: Int) {
    CircularProgressIndicator()
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
}

@StringRes
private fun errorText(error: LoginError): Int = when (error) {
    LoginError.INVALID_URL -> R.string.login_error_invalid_url
    LoginError.INSECURE_URL -> R.string.login_error_insecure
    LoginError.NOT_NEXTCLOUD -> R.string.login_error_not_nextcloud
    LoginError.UNREACHABLE -> R.string.login_error_unreachable
    LoginError.TLS_ERROR -> R.string.login_error_tls
    LoginError.EXPIRED -> R.string.login_error_expired
    LoginError.NOTES_APP_MISSING -> R.string.login_error_notes_missing
    LoginError.UNSUPPORTED_API -> R.string.login_error_unsupported_api
    LoginError.UNKNOWN -> R.string.login_error_unknown
}
