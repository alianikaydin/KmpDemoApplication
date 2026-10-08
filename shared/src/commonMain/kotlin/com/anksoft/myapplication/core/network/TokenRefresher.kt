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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Exchanges the stored refresh token for a new token pair, at most one network call at a time.
 *
 * Refresh tokens rotate: the backend revokes the whole token family when one is sent twice. So a
 * caller that lost the race must reuse what the winner stored instead of sending the old token
 * again. That is decided here, under [mutex], and does not rely on the HTTP client's own
 * single-flight behaviour.
 *
 * Tokens, e-mail addresses and user ids are never logged.
 */
internal class TokenRefresher(
    private val sessionManager: SessionManager,
    private val logger: AppLogger,
    private val onSessionExpired: () -> Unit
) {
    private val mutex = Mutex()

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
        val alreadyRefreshed = staleRefreshToken != storedRefresh ||
            (failedAccessToken != null && failedAccessToken != storedAccess)
        if (alreadyRefreshed) {
            // Another call rotated the tokens while this one waited: use them, send nothing.
            logger.debug(LogTags.SESSION) { "refresh skipped: tokens were already refreshed" }
            return BearerTokens(storedAccess, storedRefresh)
        }

        val result = safeCall<AuthResponseDto>(logger, AuthPaths.REFRESH) { call(storedRefresh) }
        if (sessionManager.getRefreshToken() != storedRefresh) {
            // The user logged out or in while the request was in flight; do not touch the new state.
            logger.debug(LogTags.SESSION) { "refresh result dropped: session changed" }
            return null
        }
        return when (result) {
            is Result.Success -> {
                val response = result.data
                // Store before returning: the retried request and any later call read from here.
                val newRefresh = response.refreshToken ?: storedRefresh
                sessionManager.saveToken(response.accessToken)
                sessionManager.saveRefreshToken(newRefresh)
                logger.info(LogTags.SESSION) { "refresh succeeded" }
                BearerTokens(response.accessToken, newRefresh)
            }

            is Result.Failure -> {
                // safeCall already logged the failure; only the consequence is logged here.
                if (result.error == DataError.Remote.UNAUTHORIZED) {
                    // Revoked, expired or reused: this session can never be refreshed again.
                    logger.warn(LogTags.SESSION) { "refresh rejected: session expired" }
                    onSessionExpired()
                }
                // Network errors, timeouts and 5xx keep the session so a retry can succeed later.
                null
            }
        }
    }
}
