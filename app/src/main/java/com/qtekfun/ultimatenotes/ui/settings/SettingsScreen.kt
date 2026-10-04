// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimatenotes.BuildConfig
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.data.auth.Account
import com.qtekfun.ultimatenotes.data.settings.AppSettings
import com.qtekfun.ultimatenotes.data.settings.ThemeMode
import com.qtekfun.ultimatenotes.ui.theme.UltimateNotesTheme

private val ROW_MIN_HEIGHT = 56.dp

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        versionName = BuildConfig.VERSION_NAME,
        dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        onBack = onBack,
        onTheme = viewModel::setTheme,
        onAmoled = viewModel::setAmoled,
        onDynamicColor = viewModel::setDynamicColor,
        onLogOut = viewModel::logOut,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
fun SettingsContent(
    state: SettingsUiState,
    versionName: String,
    dynamicColorAvailable: Boolean,
    onBack: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onAmoled: (Boolean) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onLogOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            AccountSection(state.account, state.loggingOut, onLogOut)
            AppearanceSection(
                state.settings,
                dynamicColorAvailable,
                onTheme,
                onAmoled,
                onDynamicColor
            )
            SectionHeader(R.string.settings_about)
            Text(
                text = stringResource(R.string.settings_version, versionName),
                modifier = Modifier
                    .heightIn(min = ROW_MIN_HEIGHT)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            )
        }
    }
}

@Composable
private fun AccountSection(account: Account?, loggingOut: Boolean, onLogOut: () -> Unit) {
    SectionHeader(R.string.settings_account)
    if (account != null) {
        Text(
            text = stringResource(
                R.string.settings_account_signed_in,
                account.username,
                account.serverUrl
            ),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
    OutlinedButton(
        onClick = onLogOut,
        enabled = !loggingOut,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .heightIn(min = 48.dp)
    ) {
        Text(
            stringResource(
                if (loggingOut) R.string.settings_logging_out else R.string.settings_logout
            )
        )
    }
}

@Composable
private fun AppearanceSection(
    settings: AppSettings,
    dynamicColorAvailable: Boolean,
    onTheme: (ThemeMode) -> Unit,
    onAmoled: (Boolean) -> Unit,
    onDynamicColor: (Boolean) -> Unit
) {
    SectionHeader(R.string.settings_appearance)
    Text(
        text = stringResource(R.string.settings_theme),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
    Column(Modifier.selectableGroup()) {
        ThemeMode.entries.forEach { mode ->
            ThemeRow(mode, selected = settings.theme == mode, onClick = { onTheme(mode) })
        }
    }
    SwitchRow(
        R.string.settings_amoled,
        R.string.settings_amoled_hint,
        settings.amoled,
        onAmoled
    )
    if (dynamicColorAvailable) {
        SwitchRow(
            R.string.settings_dynamic_color,
            R.string.settings_dynamic_color_hint,
            settings.dynamicColor,
            onDynamicColor
        )
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.settings_language_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionHeader(title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun ThemeRow(mode: ThemeMode, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_MIN_HEIGHT)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            stringResource(
                when (mode) {
                    ThemeMode.SYSTEM -> R.string.theme_system
                    ThemeMode.LIGHT -> R.string.theme_light
                    ThemeMode.DARK -> R.string.theme_dark
                }
            )
        )
    }
}

@Composable
private fun SwitchRow(title: Int, hint: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_MIN_HEIGHT)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Preview
@Composable
private fun SettingsPreview() {
    UltimateNotesTheme {
        SettingsContent(
            state = SettingsUiState(account = Account("https://cloud.example.com/", "ana")),
            versionName = "0.1.0",
            dynamicColorAvailable = true,
            onBack = {},
            onTheme = {},
            onAmoled = {},
            onDynamicColor = {},
            onLogOut = {}
        )
    }
}
