// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.ui.settings

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qtekfun.ultimatenotes.R
import com.qtekfun.ultimatenotes.data.backup.PassphraseBackupCipher
import com.qtekfun.ultimatenotes.domain.backup.BackupError
import com.qtekfun.ultimatenotes.domain.backup.BackupResult
import com.qtekfun.ultimatenotes.domain.backup.BackupService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Writes the encrypted backup to a file the user picked and restores from one (SPEC §8). The
 * file contents and passphrases are never logged and only live in memory while needed.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backup: BackupService,
    private val io: CoroutineDispatcher
) : ViewModel() {
    private val mutableMessages = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    private var waiting: ByteArray? = null
    private val mutableAskingPassphrase = MutableStateFlow(false)
    private val mutableBusy = MutableStateFlow(false)

    /** Messages to show, as string resources. */
    val messages: SharedFlow<Int> = mutableMessages.asSharedFlow()

    /** A chosen backup file is waiting for its passphrase. */
    val askingPassphrase: StateFlow<Boolean> = mutableAskingPassphrase.asStateFlow()

    /** Key derivation is running: it takes a moment by design. */
    val busy: StateFlow<Boolean> = mutableBusy.asStateFlow()

    fun export(target: Uri, passphrase: CharArray) {
        viewModelScope.launch {
            mutableBusy.value = true
            val message = when (val result = backup.export(passphrase)) {
                is BackupResult.Failure -> errorMessage(result.error)
                is BackupResult.Success -> withContext(io) { write(target, result.value) }
            }
            passphrase.fill('\u0000')
            mutableBusy.value = false
            mutableMessages.tryEmit(message)
        }
    }

    fun startRestore(source: Uri) {
        viewModelScope.launch {
            val bytes = withContext(io) { read(source) }
            if (bytes == null) {
                mutableMessages.tryEmit(R.string.backup_error_read)
            } else {
                waiting = bytes
                mutableAskingPassphrase.value = true
            }
        }
    }

    /** Restores the waiting file; on a wrong passphrase the dialog stays open to try again. */
    fun finishRestore(passphrase: CharArray) {
        val file = waiting ?: return
        viewModelScope.launch {
            mutableBusy.value = true
            val result = backup.restore(file, passphrase)
            passphrase.fill('\u0000')
            mutableBusy.value = false
            if (result !is BackupResult.Failure ||
                result.error != BackupError.WrongPassphraseOrCorrupt
            ) {
                cancelRestore()
            }
            mutableMessages.tryEmit(
                when (result) {
                    is BackupResult.Success -> R.string.backup_restored
                    is BackupResult.Failure -> errorMessage(result.error)
                }
            )
        }
    }

    fun cancelRestore() {
        waiting?.fill(0)
        waiting = null
        mutableAskingPassphrase.value = false
    }

    @StringRes
    private fun write(target: Uri, bytes: ByteArray): Int = try {
        val stream = context.contentResolver.openOutputStream(target, "wt")
        if (stream == null) {
            R.string.backup_error_write
        } else {
            stream.use { it.write(bytes) }
            R.string.backup_exported
        }
    } catch (_: IOException) {
        R.string.backup_error_write
    } catch (_: SecurityException) {
        R.string.backup_error_write
    }

    private fun read(source: Uri): ByteArray? = try {
        context.contentResolver.openInputStream(source)?.use(::readLimited)
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    /** At most the biggest valid backup plus one byte, so a huge file is not loaded in memory. */
    private fun readLimited(input: InputStream): ByteArray {
        val limit = PassphraseBackupCipher.MAX_FILE_BYTES + 1
        val buffer = ByteArray(limit)
        var size = 0
        while (size < limit) {
            val read = input.read(buffer, size, limit - size)
            if (read < 0) break
            size += read
        }
        return buffer.copyOf(size)
    }

    @StringRes
    private fun errorMessage(error: BackupError): Int = when (error) {
        BackupError.NotABackup -> R.string.backup_error_not_backup
        is BackupError.UnsupportedVersion -> R.string.backup_error_version
        BackupError.WrongPassphraseOrCorrupt -> R.string.backup_error_wrong_passphrase
        BackupError.InvalidContent -> R.string.backup_error_invalid
        BackupError.WeakPassphrase -> R.string.backup_error_weak
        BackupError.NotSignedIn -> R.string.backup_error_not_signed_in
        BackupError.AccountMismatch -> R.string.backup_error_mismatch
        BackupError.StorageFailed -> R.string.backup_error_storage
    }
}
