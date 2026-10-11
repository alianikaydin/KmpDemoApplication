package com.anksoft.myapplication.features.consent.data

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.endsWith
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import com.anksoft.myapplication.core.config.TestAppConfigs
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.logging.NoOpLogger
import com.anksoft.myapplication.core.network.createHttpClient
import com.anksoft.myapplication.core.storage.SessionManager
import com.anksoft.myapplication.features.consent.data.datasource.ConsentLocalDataSource
import com.anksoft.myapplication.features.consent.data.datasource.ConsentRemoteDataSource
import com.anksoft.myapplication.features.consent.data.repository.ConsentRepositoryImpl
import com.anksoft.myapplication.features.consent.domain.model.AccountConsent
import com.anksoft.myapplication.features.consent.domain.model.ConsentChoice
import com.anksoft.myapplication.features.consent.domain.model.ConsentDecision
import com.anksoft.myapplication.features.consent.domain.model.ConsentTexts
import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test

class ConsentRepositoryImplTest {

    private class Recorded(
        val method: HttpMethod,
        val path: String,
        val language: String?,
        val authorization: String?,
        val body: String
    )

    private val requests = mutableListOf<Recorded>()
    private var answerStatus = HttpStatusCode.OK
    private var answerBody = ""
    private var gate: CompletableDeferred<Unit>? = null

    /** Completed when the first request reaches the engine (it runs on another dispatcher). */
    private val requestArrived = CompletableDeferred<Unit>()

    // The real client stack, so the bearer plugin adds the token the way the app does. The session has
    // no refresh token, so a 401 comes back to the caller instead of starting a refresh.
    private val session = SessionManager(MapSettings()).apply {
        saveToken("access-1")
        saveUserId("u1")
    }
    private val client = createHttpClient(
        engine = MockEngine { request ->
            requests += Recorded(
                method = request.method,
                path = request.url.encodedPath,
                language = request.url.parameters["lang"],
                authorization = request.headers[HttpHeaders.Authorization],
                body = request.body.toByteArray().decodeToString()
            )
            requestArrived.complete(Unit)
            gate?.await()
            respond(
                content = answerBody,
                status = answerStatus,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        },
        config = TestAppConfigs.demo(),
        sessionManager = session,
        logger = NoOpLogger
    )
    private val repository = ConsentRepositoryImpl(
        remote = ConsentRemoteDataSource(client, NoOpLogger),
        local = ConsentLocalDataSource(session),
        sessionManager = session
    )

    private val textsJson = """
        {"version":2,"language":"tr","label":"Etiket","description":"Açıklama",
         "policy_url":"https://example.com/privacy"}
    """.trimIndent()
    private val grantedJson = """
        {"status":"granted","text_version":1,"text_language":"en",
         "decided_at":"2026-10-08T12:00:00Z","reconsent_required":false}
    """.trimIndent()
    private val deniedJson = """
        {"status":"denied","text_version":1,"text_language":"en",
         "decided_at":"2026-10-08T12:05:00Z","reconsent_required":false}
    """.trimIndent()

    @AfterTest
    fun tearDown() {
        client.close()
    }

    // AC-21, AC-24
    @Test
    fun textsAreRequestedForTheGivenLanguageAndMapped() = runTest {
        answerBody = textsJson

        val result = repository.getTexts("tr")

        val request = requests.single()
        assertThat(request.method).isEqualTo(HttpMethod.Get)
        assertThat(request.path).endsWith("consent/texts")
        assertThat(request.language).isEqualTo("tr")
        assertThat(result).isEqualTo(
            Result.Success(
                ConsentTexts(
                    version = 2,
                    language = "tr",
                    label = "Etiket",
                    description = "Açıklama",
                    policyUrl = "https://example.com/privacy"
                )
            )
        )
    }

    // AC-1, AC-21
    @Test
    fun textsNeedNoSession() = runTest {
        session.clear()
        answerBody = textsJson

        val result = repository.getTexts("en")

        assertThat(result).isInstanceOf(Result.Success::class)
        assertThat(requests.single().authorization).isNull()
    }

    // AC-6
    @Test
    fun theAccountsDecisionIsFetchedWithTheBearerTokenAndCached() = runTest {
        answerBody = grantedJson

        val result = repository.fetchAccountConsent()

        val request = requests.single()
        assertThat(request.method).isEqualTo(HttpMethod.Get)
        assertThat(request.path).endsWith("account/consent")
        assertThat(request.authorization).isEqualTo("Bearer access-1")
        assertThat(result).isEqualTo(Result.Success(AccountConsent(ConsentDecision.GRANTED, textVersion = 1)))
        assertThat(session.getConsentStatus()).isEqualTo("granted")
        assertThat(session.getConsentTextVersion()).isEqualTo(1)
    }

    // AC-8, AC-22
    @Test
    fun aDecisionIsPutWithTheShownVersionAndLanguageAndCachedOnSuccess() = runTest {
        answerBody = deniedJson

        val result = repository.saveDecision(ConsentChoice(granted = false, textVersion = 1, textLanguage = "en"))

        val request = requests.single()
        assertThat(request.method).isEqualTo(HttpMethod.Put)
        assertThat(request.path).endsWith("account/consent")
        assertThat(request.authorization).isEqualTo("Bearer access-1")
        assertThat(request.body).isEqualTo("""{"status":"denied","text_version":1,"text_language":"en"}""")
        assertThat(result).isEqualTo(Result.Success(AccountConsent(ConsentDecision.DENIED, textVersion = 1)))
        assertThat(session.getConsentStatus()).isEqualTo("denied")
    }

    // AC-22
    @Test
    fun anOutdatedTextVersionIsReportedAsUnprocessableAndNothingIsCached() = runTest {
        answerStatus = HttpStatusCode.UnprocessableEntity
        answerBody = """{"error":"unknown_consent_version","message":"Unknown consent text version."}"""

        val result = repository.saveDecision(ConsentChoice(granted = true, textVersion = 9, textLanguage = "en"))

        assertThat(result).isEqualTo(Result.Failure(DataError.Remote.UNPROCESSABLE))
        assertThat(session.getConsentStatus()).isNull()
    }

    // AC-28
    @Test
    fun anUnauthorizedAnswerIsReportedAndNothingIsCached() = runTest {
        answerStatus = HttpStatusCode.Unauthorized

        val result = repository.fetchAccountConsent()

        assertThat(result).isEqualTo(Result.Failure(DataError.Remote.UNAUTHORIZED))
        assertThat(session.getConsentStatus()).isNull()
    }

    // AC-12
    @Test
    fun aServerErrorIsReportedAndTheCacheKeepsItsValue() = runTest {
        session.saveConsent("granted", 1)
        answerStatus = HttpStatusCode.InternalServerError

        val result = repository.fetchAccountConsent()

        assertThat(result).isEqualTo(Result.Failure(DataError.Remote.SERVER_ERROR))
        assertThat(session.getConsentStatus()).isEqualTo("granted")
    }

    // AC-19
    @Test
    fun withoutASessionNothingIsSent() = runTest {
        session.clear()

        val fetch = repository.fetchAccountConsent()
        val save = repository.saveDecision(ConsentChoice(granted = true, textVersion = 1, textLanguage = "en"))

        assertThat(fetch).isEqualTo(Result.Failure(DataError.Remote.UNAUTHORIZED))
        assertThat(save).isEqualTo(Result.Failure(DataError.Remote.UNAUTHORIZED))
        assertThat(requests).isEmpty()
    }

    // AC-18
    @Test
    fun anAnswerThatArrivesAfterLogoutIsNotCached() = runTest {
        answerBody = grantedJson
        gate = CompletableDeferred()
        val fetching = async { repository.fetchAccountConsent() }
        requestArrived.await()
        assertThat(requests).hasSize(1)

        session.clear()
        gate?.complete(Unit)
        fetching.await()

        assertThat(session.getConsentStatus()).isNull()
        assertThat(session.getConsentTextVersion()).isNull()
    }

    // AC-18
    @Test
    fun anAnswerThatArrivesAfterAnotherAccountSignedInIsNotCached() = runTest {
        answerBody = grantedJson
        gate = CompletableDeferred()
        val fetching = async { repository.fetchAccountConsent() }
        requestArrived.await()

        session.clear()
        session.saveToken("access-2")
        session.saveUserId("u2")
        gate?.complete(Unit)
        fetching.await()

        assertThat(session.getConsentStatus()).isNull()
    }

    // AC-18
    @Test
    fun theCachedDecisionIsOnlyVisibleWhileSignedIn() = runTest {
        session.saveConsent("granted", 1)
        assertThat(repository.cachedConsent()).isEqualTo(AccountConsent(ConsentDecision.GRANTED, textVersion = 1))

        session.clear()
        session.saveConsent("granted", 1)

        assertThat(repository.isSignedIn()).isFalse()
        assertThat(repository.cachedConsent()).isNull()
    }
}
