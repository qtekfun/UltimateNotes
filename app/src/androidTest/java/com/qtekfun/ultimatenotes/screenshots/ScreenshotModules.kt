// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.screenshots

import android.content.Context
import android.content.SharedPreferences
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.qtekfun.ultimatenotes.data.api.CredentialsProvider
import com.qtekfun.ultimatenotes.data.api.NotesClientFactory
import com.qtekfun.ultimatenotes.data.auth.AccountSession
import com.qtekfun.ultimatenotes.data.auth.AccountStorage
import com.qtekfun.ultimatenotes.data.auth.EncryptedSecret
import com.qtekfun.ultimatenotes.data.auth.SecretCipher
import com.qtekfun.ultimatenotes.data.auth.StoredAccount
import com.qtekfun.ultimatenotes.data.local.UltimateNotesDatabase
import com.qtekfun.ultimatenotes.data.local.dao.NoteDao
import com.qtekfun.ultimatenotes.data.local.dao.NoteSearchDao
import com.qtekfun.ultimatenotes.data.local.dao.NoteSyncDao
import com.qtekfun.ultimatenotes.data.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * The replacements [ScreenshotTest] installs instead of the app's modules: an in-memory database,
 * a signed-in account that exists only in memory, and settings in a file of their own. Nothing
 * here touches the notes, the account or the settings of the app installed on the device, and
 * nothing talks to a network.
 */
object DemoAccount {
    // `.invalid` is reserved (RFC 2606): this address never resolves.
    const val SERVER = "https://cloud.example.invalid"
    const val USER = "demo"
}

/** An account that lives in memory only: it starts signed in and is never written anywhere. */
class InMemoryAccountStorage : AccountStorage {
    private var stored: StoredAccount? = StoredAccount(
        DemoAccount.SERVER,
        DemoAccount.USER,
        EncryptedSecret(ByteArray(0), ByteArray(0))
    )

    override fun load(): StoredAccount? = stored

    override fun save(account: StoredAccount) {
        stored = account
    }

    override fun clear() {
        stored = null
    }
}

/** No Keystore: the "password" is a placeholder and is never sent anywhere. */
class PlaceholderCipher : SecretCipher {
    override fun encrypt(plaintext: ByteArray) = EncryptedSecret(ByteArray(0), ByteArray(0))

    override fun decrypt(secret: EncryptedSecret): ByteArray = "placeholder".toByteArray()
}

/** Same file name prefix for every file the screenshot run creates; cleared before each run. */
const val DEMO_SETTINGS_FILE = "demo_screenshot_settings"

@Module
@InstallIn(SingletonComponent::class)
object DemoDatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): UltimateNotesDatabase =
        Room.inMemoryDatabaseBuilder<UltimateNotesDatabase>(context)
            .setDriver(AndroidSQLiteDriver())
            .build()

    @Provides
    fun noteDao(database: UltimateNotesDatabase): NoteDao = database.noteDao()

    @Provides
    fun noteSearchDao(database: UltimateNotesDatabase): NoteSearchDao = database.noteSearchDao()

    @Provides
    fun noteSyncDao(database: UltimateNotesDatabase): NoteSyncDao = database.noteSyncDao()
}

/** Mirrors `AuthModule`, with the in-memory account instead of the Keystore-backed one. */
@Module
@InstallIn(SingletonComponent::class)
object DemoAuthModule {
    @Provides
    @Singleton
    fun accountStorage(): AccountStorage = InMemoryAccountStorage()

    @Provides
    fun secretCipher(): SecretCipher = PlaceholderCipher()

    @Provides
    fun credentialsProvider(session: AccountSession): CredentialsProvider = session

    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder().followSslRedirects(false).build()

    @Provides
    fun json(): Json = NotesClientFactory.json

    @Provides
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
}

/** The settings of the run live in their own file, so the app's real settings stay untouched. */
@Module
@InstallIn(SingletonComponent::class)
object DemoSettingsModule {
    @Provides
    @Named(SettingsRepository.SETTINGS_PREFERENCES)
    fun settingsPreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences(DEMO_SETTINGS_FILE, Context.MODE_PRIVATE)
}
