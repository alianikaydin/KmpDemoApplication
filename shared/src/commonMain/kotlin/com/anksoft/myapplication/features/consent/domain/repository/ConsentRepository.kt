package com.anksoft.myapplication.features.consent.domain.repository

import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.features.consent.domain.model.AccountConsent
import com.anksoft.myapplication.features.consent.domain.model.ConsentChoice
import com.anksoft.myapplication.features.consent.domain.model.ConsentTexts

interface ConsentRepository {
    /** True while a session is stored. */
    fun isSignedIn(): Boolean

    /** The decision cached for the current session, or null when nothing was stored. */
    fun cachedConsent(): AccountConsent?

    /** The current consent text for [languageTag]; needs no session. */
    suspend fun getTexts(languageTag: String): Result<ConsentTexts, DataError.Remote>

    /** Reads the account's decision. On success the cache is rewritten, but only for the session that asked. */
    suspend fun fetchAccountConsent(): Result<AccountConsent, DataError.Remote>

    /** Records [choice]. On success the cache is rewritten, but only for the session that asked. */
    suspend fun saveDecision(choice: ConsentChoice): Result<AccountConsent, DataError.Remote>
}
