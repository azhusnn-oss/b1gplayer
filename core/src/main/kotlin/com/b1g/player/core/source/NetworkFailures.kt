package com.b1g.player.core.source

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Turns a network exception into something a user can act on.
 *
 * A bare "could not reach the server" hides the difference between a typo in the
 * host, a dead provider, and a platform policy blocking the request — which cost a
 * round trip to a physical device to diagnose once already.
 */
internal fun describeNetworkFailure(prefix: String, error: IOException): String {
    val detail = when {
        error is UnknownHostException -> "the host could not be resolved — check the address"
        error is SocketTimeoutException -> "the server did not respond in time"
        // Android blocks plain HTTP unless the app opts in; OkHttp surfaces that as
        // an UnknownServiceException naming the policy.
        error.message?.contains("CLEARTEXT", ignoreCase = true) == true ->
            "the app is not permitted to use plain HTTP for this host"
        error.message?.contains("trust anchor", ignoreCase = true) == true ->
            "the server's HTTPS certificate is not trusted"
        else -> error.message?.takeIf { it.isNotBlank() }
    }
    return if (detail == null) prefix else "$prefix: $detail"
}
