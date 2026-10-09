package com.anksoft.myapplication.core.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider

/**
 * Drops the bearer tokens the HTTP client has cached in memory.
 *
 * Ktor loads tokens through `loadTokens` once and keeps them. Without clearing, the previous
 * account's access token would still be attached after a logout followed by another login.
 */
fun interface AuthTokenCache {
    fun clear()
}

/** [AuthTokenCache] backed by the client's bearer auth provider. */
internal class KtorAuthTokenCache(private val client: HttpClient) : AuthTokenCache {
    override fun clear() {
        client.authProvider<BearerAuthProvider>()?.clearToken()
    }
}
