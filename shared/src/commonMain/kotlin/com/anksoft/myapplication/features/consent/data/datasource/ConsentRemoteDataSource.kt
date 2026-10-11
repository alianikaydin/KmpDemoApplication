package com.anksoft.myapplication.features.consent.data.datasource

import com.anksoft.kmpdemo.contract.consent.AccountConsentDto
import com.anksoft.kmpdemo.contract.consent.ConsentDecisionDto
import com.anksoft.kmpdemo.contract.consent.ConsentPaths
import com.anksoft.kmpdemo.contract.consent.ConsentTextsDto
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.network.safeCall
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody

/** The consent endpoints. Paths come from the contract; the bearer token is added by the HTTP client. */
class ConsentRemoteDataSource(
    private val httpClient: HttpClient,
    private val logger: AppLogger
) {

    /** Needs no session: the sign-up form shows the text before an account exists. */
    suspend fun getTexts(languageTag: String): Result<ConsentTextsDto, DataError.Remote> =
        safeCall(logger, ConsentPaths.TEXTS) {
            httpClient.get(ConsentPaths.TEXTS) {
                parameter(ConsentPaths.LANG_PARAM, languageTag)
            }
        }

    suspend fun getAccountConsent(): Result<AccountConsentDto, DataError.Remote> =
        safeCall(logger, ConsentPaths.ACCOUNT_CONSENT) {
            httpClient.get(ConsentPaths.ACCOUNT_CONSENT)
        }

    suspend fun putAccountConsent(decision: ConsentDecisionDto): Result<AccountConsentDto, DataError.Remote> =
        safeCall(logger, ConsentPaths.ACCOUNT_CONSENT) {
            httpClient.put(ConsentPaths.ACCOUNT_CONSENT) {
                setBody(decision)
            }
        }
}
