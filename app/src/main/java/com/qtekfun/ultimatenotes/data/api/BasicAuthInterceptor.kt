// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.api

import okhttp3.Credentials as OkCredentials
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Adds HTTP Basic auth from the [CredentialsProvider]. With no signed-in account the request is
 * not sent: a local 401 is returned instead, so the caller sees [ApiError.Unauthorized].
 */
class BasicAuthInterceptor(private val provider: CredentialsProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val credentials = provider.credentials() ?: return noAccount(chain.request())
        val request = chain.request().newBuilder()
            .header(
                "Authorization",
                OkCredentials.basic(credentials.username, credentials.appPassword)
            )
            .build()
        return chain.proceed(request)
    }

    private fun noAccount(request: Request): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(HTTP_UNAUTHORIZED)
        .message("No account")
        .body("".toResponseBody())
        .build()

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
