// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.domain.backup

/** Turns backup content into a versioned, passphrase-protected file and back. */
interface BackupCipher {
    /** The file bytes for [plain]; [passphrase] must not be empty. */
    fun seal(plain: ByteArray, passphrase: CharArray): ByteArray

    /** The content of [file], or why it cannot be opened. */
    fun open(file: ByteArray, passphrase: CharArray): BackupResult<ByteArray>
}
