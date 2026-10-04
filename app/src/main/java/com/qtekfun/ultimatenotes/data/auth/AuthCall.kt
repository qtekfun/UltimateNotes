// SPDX-FileCopyrightText: 2026 UltimateNotes contributors
// SPDX-License-Identifier: GPL-3.0-or-later

package com.qtekfun.ultimatenotes.data.auth

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.serialization.SerializationException
import retrofit2.Response

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_NOT_FOUND = 404

/**
 * Runs a Retrofit call and turns its response or failure into an [AuthResult]. Coroutine
 * cancellation is not caught, so it still propagates.
 */
suspend fun <T> authCall(call: suspend () -> Response<T>): AuthResult<T> = try {
    toResult(call())
} catch (_: SocketTimeoutException) {
    AuthResult.NetworkError(AuthResult.NetworkError.Kind.TIMEOUT)
} catch (_: UnknownHostException) {
    AuthResult.NetworkError(AuthResult.NetworkError.Kind.UNREACHABLE)
} catch (_: ConnectException) {
    AuthResult.NetworkError(AuthResult.NetworkError.Kind.UNREACHABLE)
} catch (_: NoRouteToHostException) {
    AuthResult.NetworkError(AuthResult.NetworkError.Kind.UNREACHABLE)
} catch (_: SSLException) {
    AuthResult.NetworkError(AuthResult.NetworkError.Kind.TLS)
} catch (_: SerializationException) {
    AuthResult.ParseError
} catch (_: IOException) {
    AuthResult.NetworkError(AuthResult.NetworkError.Kind.OTHER)
}

private fun <T> toResult(response: Response<T>): AuthResult<T> {
    val body = response.body()
    return when {
        response.code() == HTTP_UNAUTHORIZED -> AuthResult.Unauthorized
        response.code() == HTTP_NOT_FOUND -> AuthResult.NotFound
        !response.isSuccessful -> AuthResult.HttpError(response.code())
        body == null -> AuthResult.ParseError
        else -> AuthResult.Success(body)
    }
}
