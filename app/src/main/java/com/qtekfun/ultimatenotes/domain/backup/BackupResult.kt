// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.backup

/** Why a backup could not be written or read. Nothing is applied when one of these is returned. */
sealed interface BackupError {
    /** The file does not start with the UltimateNotes backup header. */
    data object NotABackup : BackupError

    /** A backup written by a newer (or unknown) version of the format. */
    data class UnsupportedVersion(val version: Int) : BackupError

    /**
     * Wrong passphrase, or the file was truncated or altered. Authenticated encryption cannot
     * tell these apart, and neither should the message.
     */
    data object WrongPassphraseOrCorrupt : BackupError

    /** Decrypted fine, but the content is not a valid backup (e.g. an insecure server URL). */
    data object InvalidContent : BackupError

    /** The passphrase is shorter than [BackupService.MIN_PASSPHRASE_LENGTH]. */
    data object WeakPassphrase : BackupError

    /** Export needs a signed-in account. */
    data object NotSignedIn : BackupError

    /** Another account is signed in; its notes must not be mixed with the backup's account. */
    data object AccountMismatch : BackupError

    /** The account could not be stored (e.g. the Keystore key is unavailable). */
    data object StorageFailed : BackupError
}

/** Outcome of a backup operation: a value, or a sealed [BackupError]. */
sealed interface BackupResult<out T> {
    data class Success<T>(val value: T) : BackupResult<T>

    data class Failure(val error: BackupError) : BackupResult<Nothing>
}
