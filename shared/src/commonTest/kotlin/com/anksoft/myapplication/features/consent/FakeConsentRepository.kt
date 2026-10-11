package com.anksoft.myapplication.features.consent

import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.features.consent.domain.model.AccountConsent
import com.anksoft.myapplication.features.consent.domain.model.ConsentChoice
import com.anksoft.myapplication.features.consent.domain.model.ConsentDecision
import com.anksoft.myapplication.features.consent.domain.model.ConsentTexts
import com.anksoft.myapplication.features.consent.domain.repository.ConsentRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * In-memory [ConsentRepository]. Results are plain properties; a gate holds the call until the
 * test opens it. The result is read when the call starts, so a test can change the property for
 * the next call while an earlier one is still waiting (an answer that arrives late).
 */
class FakeConsentRepository : ConsentRepository {

    var signedIn = true
    var cached: AccountConsent? = null

    var fetchResult: Result<AccountConsent, DataError.Remote> =
        Result.Success(AccountConsent(ConsentDecision.NONE, textVersion = null))
    var saveResult: Result<AccountConsent, DataError.Remote>? = null
    var textsProvider: (String) -> Result<ConsentTexts, DataError.Remote> = { tag ->
        Result.Success(texts(language = tag))
    }

    var fetchGate: CompletableDeferred<Unit>? = null
    var saveGate: CompletableDeferred<Unit>? = null
    var textsGate: CompletableDeferred<Unit>? = null

    /** When true, a waiting call keeps waiting even if its caller is cancelled (a response already on the wire). */
    var ignoreCancellation = false

    var fetchCount = 0
        private set
    val savedChoices = mutableListOf<ConsentChoice>()
    val textRequests = mutableListOf<String>()

    override fun isSignedIn(): Boolean = signedIn

    override fun cachedConsent(): AccountConsent? = cached

    override suspend fun getTexts(languageTag: String): Result<ConsentTexts, DataError.Remote> {
        textRequests += languageTag
        val result = textsProvider(languageTag)
        await(textsGate)
        return result
    }

    override suspend fun fetchAccountConsent(): Result<AccountConsent, DataError.Remote> {
        fetchCount++
        val result = fetchResult
        await(fetchGate)
        return result
    }

    override suspend fun saveDecision(choice: ConsentChoice): Result<AccountConsent, DataError.Remote> {
        savedChoices += choice
        // A save that was not given a result mirrors the choice, like the backend does.
        val result = saveResult ?: Result.Success(
            AccountConsent(
                decision = if (choice.granted) ConsentDecision.GRANTED else ConsentDecision.DENIED,
                textVersion = choice.textVersion
            )
        )
        await(saveGate)
        return result
    }

    private suspend fun await(gate: CompletableDeferred<Unit>?) {
        if (gate == null) return
        if (ignoreCancellation) withContext(NonCancellable) { gate.await() } else gate.await()
    }

    companion object {
        fun texts(
            version: Int = 1,
            language: String = "en",
            policyUrl: String = "https://example.com/privacy"
        ) = ConsentTexts(
            version = version,
            language = language,
            label = "Allow optional data collection ($language)",
            description = "Crash reports and usage statistics ($language).",
            policyUrl = policyUrl
        )
    }
}
