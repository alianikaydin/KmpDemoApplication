package com.anksoft.myapplication.features.consent.data.repository

import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.domain.map
import com.anksoft.myapplication.core.domain.onSuccess
import com.anksoft.myapplication.core.storage.SessionManager
import com.anksoft.myapplication.features.consent.data.datasource.ConsentLocalDataSource
import com.anksoft.myapplication.features.consent.data.datasource.ConsentRemoteDataSource
import com.anksoft.myapplication.features.consent.data.mapper.toDomain
import com.anksoft.myapplication.features.consent.data.mapper.toDto
import com.anksoft.myapplication.features.consent.domain.model.AccountConsent
import com.anksoft.myapplication.features.consent.domain.model.ConsentChoice
import com.anksoft.myapplication.features.consent.domain.model.ConsentTexts
import com.anksoft.myapplication.features.consent.domain.repository.ConsentRepository

class ConsentRepositoryImpl(
    private val remote: ConsentRemoteDataSource,
    private val local: ConsentLocalDataSource,
    private val sessionManager: SessionManager
) : ConsentRepository {

    override fun isSignedIn(): Boolean = sessionManager.getToken() != null

    override fun cachedConsent(): AccountConsent? = if (isSignedIn()) local.read() else null

    override suspend fun getTexts(languageTag: String): Result<ConsentTexts, DataError.Remote> =
        remote.getTexts(languageTag).map { it.toDomain() }

    override suspend fun fetchAccountConsent(): Result<AccountConsent, DataError.Remote> =
        forCurrentSession { userId ->
            remote.getAccountConsent()
                .map { it.toDomain() }
                .onSuccess { local.writeForUser(userId, it) }
        }

    override suspend fun saveDecision(choice: ConsentChoice): Result<AccountConsent, DataError.Remote> =
        forCurrentSession { userId ->
            remote.putAccountConsent(choice.toDto())
                .map { it.toDomain() }
                .onSuccess { local.writeForUser(userId, it) }
        }

    /**
     * Runs [request] for the session that is stored now. Without a session nothing is sent. The
     * answer is cached only if that session is still the stored one when it arrives, so a slow
     * answer cannot put one account's decision into the next account's cache.
     */
    private suspend fun forCurrentSession(
        request: suspend (userId: String) -> Result<AccountConsent, DataError.Remote>
    ): Result<AccountConsent, DataError.Remote> {
        val userId = sessionManager.getUserId()
        if (sessionManager.getToken() == null || userId == null) {
            return Result.Failure(DataError.Remote.UNAUTHORIZED)
        }
        return request(userId)
    }
}
