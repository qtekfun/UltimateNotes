// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.backup

import com.qtekfun.ultimatenotes.data.api.Credentials
import com.qtekfun.ultimatenotes.data.auth.AccountSession
import com.qtekfun.ultimatenotes.data.auth.ServerUrl
import com.qtekfun.ultimatenotes.data.settings.AppSettings
import com.qtekfun.ultimatenotes.data.settings.SettingsRepository
import java.security.GeneralSecurityException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Encrypted backup of the settings and the account (SPEC §8). The file is sealed with a
 * passphrase the user chooses, because the Keystore key cannot leave the phone. A restore
 * validates everything first and only then applies it, so a bad file changes nothing.
 * Passphrases, the app password and the file contents are never logged.
 */
class BackupService @Inject constructor(
    private val cipher: BackupCipher,
    private val settings: SettingsRepository,
    private val session: AccountSession,
    private val io: CoroutineDispatcher
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** The backup file bytes, sealed with [passphrase]. */
    suspend fun export(passphrase: CharArray): BackupResult<ByteArray> = withContext(io) {
        if (passphrase.size < MIN_PASSPHRASE_LENGTH) {
            return@withContext BackupResult.Failure(BackupError.WeakPassphrase)
        }
        val account = session.activeAccount.value ?: session.restore()
        val credentials = session.credentials()
        if (account == null || credentials == null) {
            return@withContext BackupResult.Failure(BackupError.NotSignedIn)
        }
        val payload = BackupPayload(
            settings = settings.current.toBackup(),
            account = BackupAccount(
                account.serverUrl,
                credentials.username,
                credentials.appPassword
            )
        )
        val plain = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
        try {
            BackupResult.Success(cipher.seal(plain, passphrase))
        } finally {
            plain.fill(0)
        }
    }

    /** Applies the backup in [file]: signs the account in again and restores the settings. */
    suspend fun restore(file: ByteArray, passphrase: CharArray): BackupResult<Unit> =
        withContext(io) {
            when (val opened = cipher.open(file, passphrase)) {
                is BackupResult.Failure -> opened
                is BackupResult.Success -> apply(opened.value)
            }
        }

    private suspend fun apply(plain: ByteArray): BackupResult<Unit> {
        val payload = parse(plain)
        val server = payload?.let {
            ServerUrl.parse(it.account.serverUrl) as? ServerUrl.ParseResult.Valid
        }
        return if (payload == null || server == null || !payload.account.isComplete()) {
            BackupResult.Failure(BackupError.InvalidContent)
        } else if (!canReplaceAccount(server.url, payload.account)) {
            BackupResult.Failure(BackupError.AccountMismatch)
        } else {
            signInAndApply(server.url, payload)
        }
    }

    /** A signed-in account may only be refreshed by a backup of itself. */
    private suspend fun canReplaceAccount(server: ServerUrl, backup: BackupAccount): Boolean {
        val signedIn = session.activeAccount.value ?: session.restore()
        return signedIn == null ||
            (signedIn.serverUrl == server.toString() && signedIn.username == backup.username)
    }

    /** The account goes first: if the Keystore refuses it, no setting has been touched. */
    private suspend fun signInAndApply(server: ServerUrl, payload: BackupPayload) = try {
        session.signIn(server, Credentials(payload.account.username, payload.account.appPassword))
        with(payload.settings) {
            settings.setTheme(theme)
            settings.setAmoled(amoled)
            settings.setDynamicColor(dynamicColor)
            settings.setSortOrder(sortOrder)
            settings.setSyncInterval(syncInterval)
            settings.setSyncNetwork(syncNetwork)
            settings.setLockTimeout(lockTimeout)
            settings.setSecureWindow(secureWindow)
        }
        BackupResult.Success(Unit)
    } catch (_: GeneralSecurityException) {
        BackupResult.Failure(BackupError.StorageFailed)
    }

    private fun parse(plain: ByteArray): BackupPayload? = try {
        json.decodeFromString<BackupPayload>(plain.toString(Charsets.UTF_8))
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun BackupAccount.isComplete() = username.isNotBlank() && appPassword.isNotEmpty()

    private fun AppSettings.toBackup() = BackupSettings(
        theme = theme,
        amoled = amoled,
        dynamicColor = dynamicColor,
        sortOrder = sortOrder,
        syncInterval = syncInterval,
        syncNetwork = syncNetwork,
        lockTimeout = lockTimeout,
        secureWindow = secureWindow
    )

    companion object {
        const val MIN_PASSPHRASE_LENGTH = 8
    }
}
