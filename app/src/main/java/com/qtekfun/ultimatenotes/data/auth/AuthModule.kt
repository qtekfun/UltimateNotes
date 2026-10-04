// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.auth

import com.qtekfun.ultimatenotes.data.api.CredentialsProvider
import com.qtekfun.ultimatenotes.data.api.NotesClientFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/** Wiring of the account, Keystore and network pieces. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds
    abstract fun accountStorage(impl: SharedPreferencesAccountStorage): AccountStorage

    @Binds
    abstract fun secretCipher(impl: AndroidKeystoreCipher): SecretCipher

    @Binds
    abstract fun credentialsProvider(impl: AccountSession): CredentialsProvider

    companion object {
        @Provides
        @Singleton
        fun okHttpClient(): OkHttpClient = OkHttpClient.Builder().followSslRedirects(false).build()

        @Provides
        fun json(): Json = NotesClientFactory.json

        @Provides
        fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}
