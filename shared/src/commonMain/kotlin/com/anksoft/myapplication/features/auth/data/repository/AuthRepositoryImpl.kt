package com.anksoft.myapplication.features.auth.data.repository

import com.anksoft.kmpdemo.contract.auth.AuthResponseDto
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.domain.map
import com.anksoft.myapplication.core.domain.onFailure
import com.anksoft.myapplication.core.domain.onSuccess
import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.info
import com.anksoft.myapplication.core.logging.warn
import com.anksoft.myapplication.core.network.AuthTokenCache
import com.anksoft.myapplication.core.session.SessionObserver
import com.anksoft.myapplication.core.session.SignOutReason
import com.anksoft.myapplication.core.storage.SessionManager
import com.anksoft.myapplication.features.auth.data.datasource.AuthRemoteDataSource
import com.anksoft.myapplication.features.auth.data.mapper.toUser
import com.anksoft.myapplication.features.auth.domain.model.User
import com.anksoft.myapplication.features.auth.domain.repository.AuthRepository
import com.anksoft.myapplication.features.consent.data.datasource.ConsentLocalDataSource
import com.anksoft.myapplication.features.consent.data.mapper.toDomain
import com.anksoft.myapplication.features.consent.data.mapper.toDto
import com.anksoft.myapplication.features.consent.domain.model.ConsentChoice

class AuthRepositoryImpl(
    private val remoteDataSource: AuthRemoteDataSource,
    private val sessionManager: SessionManager,
    private val tokenCache: AuthTokenCache,
    private val consentLocal: ConsentLocalDataSource,
    private val sessionObservers: List<SessionObserver>,
    private val logger: AppLogger
) : AuthRepository {

    override suspend fun login(email: String, password: String): Result<User, DataError> =
        remoteDataSource.login(email, password).map { it.persistSession() }.logOutcome("login")

    override suspend fun register(
        email: String,
        password: String,
        consent: ConsentChoice?
    ): Result<User, DataError> =
        remoteDataSource.register(email, password, consent?.toDto()).map { it.persistSession() }.logOutcome("register")

    override suspend fun logout() {
        // One step against token refresh and session expiry running on other threads.
        sessionManager.withSessionLock {
            sessionManager.clear()
            tokenCache.clear()
            sessionObservers.forEach { it.onSignedOut(SignOutReason.USER_LOGOUT) }
        }
    }

    override suspend fun getCurrentUser(): User? {
        val id = sessionManager.getUserId() ?: return null
        val email = sessionManager.getUserEmail() ?: return null
        return User(id = id, email = email, name = sessionManager.getUserName())
    }

    /** Logs only the action and the error type; never the email, user id or tokens. */
    private fun Result<User, DataError.Remote>.logOutcome(action: String) =
        onSuccess { logger.info(LogTags.AUTH) { "$action succeeded" } }
            .onFailure { error -> logger.warn(LogTags.AUTH) { "$action failed error=$error" } }

    /**
     * Persists tokens and the identity needed to rehydrate getCurrentUser() on
     * next launch. The password is never stored (AC-1.8).
     */
    private fun AuthResponseDto.persistSession(): User {
        val domainUser = user.toUser()
        // One step against token refresh and session expiry running on other threads.
        sessionManager.withSessionLock {
            // Drop the previous account's cached bearer token before the new one is stored.
            tokenCache.clear()
            sessionManager.saveToken(accessToken)
            refreshToken?.let(sessionManager::saveRefreshToken)
            sessionManager.saveUserId(domainUser.id)
            sessionManager.saveUserEmail(domainUser.email)
            domainUser.name?.let(sessionManager::saveUserName)
            // Only the register response carries a decision; a login starts from an empty cache and
            // the consent manager fetches the account's decision once it hears about the sign-in.
            consentLocal.clear()
            consent?.toDomain()?.let(consentLocal::write)
            // Observers run last, so they see the stored session.
            sessionObservers.forEach { it.onSignedIn() }
        }
        return domainUser
    }
}
