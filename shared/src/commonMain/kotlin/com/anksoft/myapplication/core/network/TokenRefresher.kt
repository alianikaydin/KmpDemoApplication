package com.anksoft.myapplication.core.network

import com.anksoft.kmpdemo.contract.auth.AuthPaths
import com.anksoft.kmpdemo.contract.auth.AuthResponseDto
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.debug
import com.anksoft.myapplication.core.logging.info
import com.anksoft.myapplication.core.logging.warn
import com.anksoft.myapplication.core.storage.SessionManager
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Exchanges the stored refresh token for a new token pair, at most one network call at a time.
 *
 * Refresh tokens rotate: the backend revokes the whole token family when one is sent twice. So a
 * caller that lost the race must reuse what the winner stored instead of sending the old token
 * again. That is decided here, under [mutex], and does not rely on the HTTP client's own
 * single-flight behaviour.
 *
 * The network call, the storing of the new tokens and the expiry run non-cancellable: a caller that
 * leaves while the request is in flight must not leave the consumed refresh token in the store.
 *
 * Tokens, e-mail addresses and user ids are never logged.
 */
internal class TokenRefresher(
    private val sessionManager: SessionManager,
    private val logger: AppLogger,
    private val onSessionExpired: () -> Unit
) {
    private val mutex = Mutex()

    // Access tokens of the current session that this refresher replaced or issued, and the refresh
    // token that session lineage currently ends in. A failed request whose access token is not in
    // the set belongs to another session (an account switch) and is never retried. Guarded by [mutex].
    private val knownAccessTokens = mutableSetOf<String>()
    private var lineageRefreshToken: String? = null

    /**
     * @param staleRefreshToken the refresh token the failed request's tokens carried.
     * @param failedAccessToken the access token the failed request was sent with, if known.
     * @param call sends the refresh request with the given refresh token.
     * @return the tokens to retry with, or null when the request should not be retried.
     */
    suspend fun refresh(
        staleRefreshToken: String?,
        failedAccessToken: String? = null,
        call: suspend (refreshToken: String) -> HttpResponse
    ): BearerTokens? = mutex.withLock { refreshLocked(staleRefreshToken, failedAccessToken, call) }

    private suspend fun refreshLocked(
        staleRefreshToken: String?,
        failedAccessToken: String?,
        call: suspend (refreshToken: String) -> HttpResponse
    ): BearerTokens? {
        val storedAccess = sessionManager.getToken()
        val storedRefresh = sessionManager.getRefreshToken()
        if (storedAccess == null || storedRefresh == null) {
            // Logged out, expired by an earlier call, or the backend issues no refresh tokens.
            logger.debug(LogTags.SESSION) { "refresh skipped: no stored refresh token" }
            return null
        }
        if (lineageRefreshToken != null && lineageRefreshToken != storedRefresh) {
            // Someone signed in again since the last refresh: the old lineage is gone.
            knownAccessTokens.clear()
            lineageRefreshToken = null
        }
        if (failedAccessToken != null && failedAccessToken != storedAccess) {
            if (failedAccessToken !in knownAccessTokens) {
                // The request was sent under a different session; retrying would use this one's token.
                logger.debug(LogTags.SESSION) { "refresh skipped: request belongs to another session" }
                return null
            }
            return alreadyRefreshed(storedAccess, storedRefresh)
        }
        if (staleRefreshToken != storedRefresh) return alreadyRefreshed(storedAccess, storedRefresh)

        // A caller that leaves now must not abandon a refresh token the server may already have consumed.
        return withContext(NonCancellable) { exchange(storedAccess, storedRefresh, call) }
    }

    /** Another call rotated the tokens while this one waited: use them, send nothing. */
    private fun alreadyRefreshed(storedAccess: String, storedRefresh: String): BearerTokens {
        logger.debug(LogTags.SESSION) { "refresh skipped: tokens were already refreshed" }
        return BearerTokens(storedAccess, storedRefresh)
    }

    private suspend fun exchange(
        storedAccess: String,
        storedRefresh: String,
        call: suspend (refreshToken: String) -> HttpResponse
    ): BearerTokens? {
        val result = safeCall<AuthResponseDto>(logger, AuthPaths.REFRESH) { call(storedRefresh) }
        return when (result) {
            is Result.Success -> {
                val response = result.data
                // Store before returning: the retried request and any later call read from here.
                // Compare and write happen as one step, so a login on another thread is never
                // overwritten with the old account's tokens.
                val newRefresh = response.refreshToken ?: storedRefresh
                val stored = sessionManager.replaceTokensIfRefreshTokenIs(
                    expectedRefreshToken = storedRefresh,
                    accessToken = response.accessToken,
                    refreshToken = newRefresh
                )
                if (!stored) {
                    // The user logged out or in while the request was in flight; leave the new state alone.
                    logger.debug(LogTags.SESSION) { "refresh result dropped: session changed" }
                    return null
                }
                knownAccessTokens += storedAccess
                knownAccessTokens += response.accessToken
                lineageRefreshToken = newRefresh
                logger.info(LogTags.SESSION) { "refresh succeeded" }
                BearerTokens(response.accessToken, newRefresh)
            }

            is Result.Failure -> {
                // safeCall already logged the failure; only the consequence is logged here.
                if (result.error == DataError.Remote.UNAUTHORIZED) {
                    // Revoked, expired or reused: this session can never be refreshed again. A
                    // session that was replaced in the meantime is not this one's to end.
                    val expired = sessionManager.withSessionLock {
                        val unchanged = sessionManager.getRefreshToken() == storedRefresh
                        if (unchanged) onSessionExpired()
                        unchanged
                    }
                    if (expired) {
                        logger.warn(LogTags.SESSION) { "refresh rejected: session expired" }
                        knownAccessTokens.clear()
                        lineageRefreshToken = null
                    } else {
                        logger.debug(LogTags.SESSION) { "refresh result dropped: session changed" }
                    }
                }
                // Network errors, timeouts and 5xx keep the session so a retry can succeed later.
                null
            }
        }
    }
}
