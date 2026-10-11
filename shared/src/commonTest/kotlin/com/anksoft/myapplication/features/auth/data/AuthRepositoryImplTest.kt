package com.anksoft.myapplication.features.auth.data

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isEmpty
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.logging.LogSeverity
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.NoOpLogger
import com.anksoft.myapplication.core.logging.recordingLogger
import com.anksoft.myapplication.core.network.AuthTokenCache
import com.anksoft.myapplication.core.network.FakeAuthTokenCache
import com.anksoft.myapplication.core.session.FakeSessionObserver
import com.anksoft.myapplication.core.session.SessionObserver
import com.anksoft.myapplication.core.session.SignOutReason
import com.anksoft.myapplication.core.storage.SessionManager
import com.anksoft.myapplication.features.auth.data.datasource.AuthRemoteDataSource
import com.anksoft.myapplication.features.auth.data.repository.AuthRepositoryImpl
import com.anksoft.myapplication.features.auth.domain.model.User
import com.anksoft.myapplication.features.consent.data.datasource.ConsentLocalDataSource
import com.anksoft.myapplication.features.consent.domain.model.ConsentChoice
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test

class AuthRepositoryImplTest {

    /** Bodies of the requests the repository sent, in order. */
    private val sentBodies = mutableListOf<String>()

    private val successBody = """
        {
          "access_token": "access-123",
          "refresh_token": "refresh-456",
          "user": { "id": "u1", "email": "user@example.com", "name": "Test User" }
        }
    """.trimIndent()

    private fun repositoryReturning(
        status: HttpStatusCode,
        body: String,
        logger: AppLogger = NoOpLogger,
        tokenCache: AuthTokenCache = FakeAuthTokenCache(),
        observers: List<SessionObserver> = emptyList()
    ): Pair<AuthRepositoryImpl, SessionManager> {
        val engine = MockEngine { request ->
            sentBodies += request.body.toByteArray().decodeToString()
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
            defaultRequest {
                url("https://test.local/")
                contentType(ContentType.Application.Json)
            }
        }
        val sessionManager = SessionManager(MapSettings())
        val repository = AuthRepositoryImpl(
            AuthRemoteDataSource(client, logger),
            sessionManager,
            tokenCache,
            ConsentLocalDataSource(sessionManager),
            observers,
            logger
        )
        return repository to sessionManager
    }

    @Test
    fun successfulLoginReturnsUserAndPersistsSession() = runTest {
        val (repository, session) = repositoryReturning(HttpStatusCode.OK, successBody)

        val result = repository.login("user@example.com", "Password1")

        assertThat(result).isEqualTo(
            Result.Success(User(id = "u1", email = "user@example.com", name = "Test User"))
        )
        assertThat(session.getToken()).isEqualTo("access-123")
        assertThat(session.getRefreshToken()).isEqualTo("refresh-456")
        assertThat(session.getUserId()).isEqualTo("u1")
        assertThat(session.getUserEmail()).isEqualTo("user@example.com")
    }

    @Test
    fun unauthorizedMapsToUnauthorizedAndStoresNothing() = runTest {
        val (repository, session) = repositoryReturning(HttpStatusCode.Unauthorized, "{}")

        val result = repository.login("user@example.com", "wrong")

        assertThat(result).isEqualTo(Result.Failure(DataError.Remote.UNAUTHORIZED))
        assertThat(session.getToken()).isNull()
    }

    @Test
    fun conflictOnRegisterMapsToConflict() = runTest {
        val (repository, _) = repositoryReturning(HttpStatusCode.Conflict, "{}")

        val result = repository.register("taken@example.com", "Password1", consent = null)

        assertThat(result).isEqualTo(Result.Failure(DataError.Remote.CONFLICT))
    }

    @Test
    fun tooManyRequestsMapsToTooManyRequests() = runTest {
        val (repository, _) = repositoryReturning(HttpStatusCode.TooManyRequests, "{}")

        assertThat(repository.login("user@example.com", "Password1"))
            .isEqualTo(Result.Failure(DataError.Remote.TOO_MANY_REQUESTS))
    }

    @Test
    fun serverErrorMapsToServerError() = runTest {
        val (repository, _) = repositoryReturning(HttpStatusCode.InternalServerError, "{}")

        assertThat(repository.login("user@example.com", "Password1"))
            .isEqualTo(Result.Failure(DataError.Remote.SERVER_ERROR))
    }

    @Test
    fun malformedBodyMapsToSerialization() = runTest {
        val (repository, session) = repositoryReturning(
            HttpStatusCode.OK,
            """{ "unexpected": true }"""
        )

        assertThat(repository.login("user@example.com", "Password1"))
            .isEqualTo(Result.Failure(DataError.Remote.SERIALIZATION))
        assertThat(session.getToken()).isNull()
    }

    @Test
    fun responseWithoutRefreshTokenStillPersistsAccessToken() = runTest {
        val (repository, session) = repositoryReturning(
            HttpStatusCode.OK,
            """
            {
              "access_token": "access-only",
              "user": { "id": "u1", "email": "user@example.com" }
            }
            """.trimIndent()
        )

        assertThat(repository.login("user@example.com", "Password1"))
            .isInstanceOf(Result.Success::class)
        assertThat(session.getToken()).isEqualTo("access-only")
        assertThat(session.getRefreshToken()).isNull()
    }

    @Test
    fun logoutClearsTheSession() = runTest {
        val (repository, session) = repositoryReturning(HttpStatusCode.OK, successBody)
        repository.login("user@example.com", "Password1")

        repository.logout()

        assertThat(session.getToken()).isNull()
        assertThat(session.getRefreshToken()).isNull()
        assertThat(session.getUserId()).isNull()
    }

    @Test
    fun getCurrentUserRehydratesFromStoredSession() = runTest {
        val (repository, _) = repositoryReturning(HttpStatusCode.OK, successBody)
        repository.login("user@example.com", "Password1")

        assertThat(repository.getCurrentUser())
            .isEqualTo(User(id = "u1", email = "user@example.com", name = "Test User"))
    }

    @Test
    fun getCurrentUserIsNullWithNoSession() = runTest {
        val (repository, _) = repositoryReturning(HttpStatusCode.OK, successBody)

        assertThat(repository.getCurrentUser()).isNull()
    }

    // AC-1
    @Test
    fun successfulLoginLogsInfoWithoutEmailOrTokens() = runTest {
        val (logger, writer) = recordingLogger()
        val (repository, _) = repositoryReturning(HttpStatusCode.OK, successBody, logger)

        repository.login("user@example.com", "Password1")

        val entry = writer.entries.single { it.tag == LogTags.AUTH }
        assertThat(entry.severity).isEqualTo(LogSeverity.INFO)
        assertThat(entry.message).contains("login succeeded")
        val all = writer.entries.joinToString { it.message }
        assertThat(all).doesNotContain("user@example.com")
        assertThat(all).doesNotContain("access-123")
        assertThat(all).doesNotContain("u1")
    }

    // AC-1
    @Test
    fun failedLoginLogsWarningWithErrorTypeOnly() = runTest {
        val (logger, writer) = recordingLogger()
        val (repository, _) = repositoryReturning(HttpStatusCode.Unauthorized, "{}", logger)

        repository.login("user@example.com", "wrong")

        val entry = writer.entries.single { it.tag == LogTags.AUTH }
        assertThat(entry.severity).isEqualTo(LogSeverity.WARN)
        assertThat(entry.message).contains("login failed error=UNAUTHORIZED")
        assertThat(writer.entries.joinToString { it.message }).doesNotContain("user@example.com")
    }

    // AC-1
    @Test
    fun registerOutcomeIsLoggedUnderItsOwnAction() = runTest {
        val (logger, writer) = recordingLogger()
        val (repository, _) = repositoryReturning(HttpStatusCode.Conflict, "{}", logger)

        repository.register("taken@example.com", "Password1", consent = null)

        assertThat(writer.entries.single { it.tag == LogTags.AUTH }.message)
            .contains("register failed error=CONFLICT")
    }

    // AC-18
    @Test
    fun loginAndRegisterClearTheBearerCacheBeforeStoringTheNewSession() = runTest {
        val cache = FakeAuthTokenCache()
        val (repository, _) = repositoryReturning(HttpStatusCode.OK, successBody, tokenCache = cache)

        repository.login("user@example.com", "Password1")
        assertThat(cache.clearCount).isEqualTo(1)

        repository.register("user@example.com", "Password1", consent = null)
        assertThat(cache.clearCount).isEqualTo(2)
    }

    // AC-18
    @Test
    fun failedLoginLeavesTheBearerCacheAlone() = runTest {
        val cache = FakeAuthTokenCache()
        val (repository, _) = repositoryReturning(HttpStatusCode.Unauthorized, "{}", tokenCache = cache)

        repository.login("user@example.com", "wrong")

        assertThat(cache.clearCount).isEqualTo(0)
    }

    // AC-18
    @Test
    fun logoutClearsTheBearerCache() = runTest {
        val cache = FakeAuthTokenCache()
        val (repository, _) = repositoryReturning(HttpStatusCode.OK, successBody, tokenCache = cache)

        repository.logout()

        assertThat(cache.clearCount).isEqualTo(1)
    }

    // AC-28
    @Test
    fun observersLearnAboutSignInOnlyAfterTheSessionWasStored() = runTest {
        val observer = FakeSessionObserver()
        val (repository, session) = repositoryReturning(HttpStatusCode.OK, successBody, observers = listOf(observer))
        var tokenSeenByObserver: String? = null
        observer.onEvent = { tokenSeenByObserver = session.getToken() }

        repository.login("user@example.com", "Password1")

        assertThat(observer.events).isEqualTo(listOf(FakeSessionObserver.Event.SignedIn))
        assertThat(tokenSeenByObserver).isEqualTo("access-123")
    }

    // AC-28
    @Test
    fun registerNotifiesObserversOfSignIn() = runTest {
        val observer = FakeSessionObserver()
        val (repository, _) = repositoryReturning(HttpStatusCode.OK, successBody, observers = listOf(observer))

        repository.register("user@example.com", "Password1", consent = null)

        assertThat(observer.events).isEqualTo(listOf(FakeSessionObserver.Event.SignedIn))
    }

    // AC-28
    @Test
    fun failedLoginDoesNotNotifyObservers() = runTest {
        val observer = FakeSessionObserver()
        val (repository, _) = repositoryReturning(HttpStatusCode.Unauthorized, "{}", observers = listOf(observer))

        repository.login("user@example.com", "wrong")

        assertThat(observer.events).isEmpty()
    }

    // AC-28
    @Test
    fun logoutNotifiesObserversSynchronouslyAfterTheSessionWasCleared() = runTest {
        val observer = FakeSessionObserver()
        val (repository, session) = repositoryReturning(HttpStatusCode.OK, successBody, observers = listOf(observer))
        repository.login("user@example.com", "Password1")
        observer.events.clear()
        var tokenSeenByObserver: String? = "unset"
        observer.onEvent = { tokenSeenByObserver = session.getToken() }

        repository.logout()

        assertThat(observer.events)
            .isEqualTo(listOf(FakeSessionObserver.Event.SignedOut(SignOutReason.USER_LOGOUT)))
        assertThat(tokenSeenByObserver).isNull()
    }

    private val registerBodyWithConsent = """
        {
          "access_token": "access-123",
          "refresh_token": "refresh-456",
          "user": { "id": "u1", "email": "user@example.com" },
          "consent": { "status": "granted", "text_version": 2, "text_language": "tr",
                       "decided_at": "2026-10-09T10:00:00Z", "reconsent_required": false }
        }
    """.trimIndent()

    // AC-2, AC-3, AC-22
    @Test
    fun registerSendsTheChoiceWithTheVersionAndLanguageOfTheTextThatWasShown() = runTest {
        val (repository, _) = repositoryReturning(HttpStatusCode.OK, successBody)

        repository.register("user@example.com", "Password1", ConsentChoice(true, textVersion = 2, textLanguage = "tr"))

        assertThat(sentBodies.single()).contains(
            """"consent":{"status":"granted","text_version":2,"text_language":"tr"}"""
        )
    }

    // AC-2
    @Test
    fun registerSendsDeniedWhenTheBoxWasLeftUnchecked() = runTest {
        val (repository, _) = repositoryReturning(HttpStatusCode.OK, successBody)

        repository.register("user@example.com", "Password1", ConsentChoice(false, textVersion = 1, textLanguage = "en"))

        assertThat(sentBodies.single()).contains(""""status":"denied"""")
    }

    // AC-4
    @Test
    fun registerWithoutAChoiceSendsNoConsentField() = runTest {
        val (repository, _) = repositoryReturning(HttpStatusCode.OK, successBody)

        repository.register("user@example.com", "Password1", consent = null)

        assertThat(sentBodies.single()).doesNotContain("consent")
    }

    // AC-3, AC-27
    @Test
    fun theDecisionInTheRegisterResponseIsCachedWithOnlyStatusAndVersion() = runTest {
        val (repository, session) = repositoryReturning(HttpStatusCode.OK, registerBodyWithConsent)

        repository.register("user@example.com", "Password1", ConsentChoice(true, 2, "tr"))

        assertThat(session.getConsentStatus()).isEqualTo("granted")
        assertThat(session.getConsentTextVersion()).isEqualTo(2)
    }

    // AC-6, AC-18
    @Test
    fun loginStartsWithoutACachedDecisionSoTheManagerFetchesTheAccountsOwn() = runTest {
        val (repository, session) = repositoryReturning(HttpStatusCode.OK, successBody)
        session.saveConsent("granted", 1)

        repository.login("user@example.com", "Password1")

        assertThat(session.getConsentStatus()).isNull()
        assertThat(session.getConsentTextVersion()).isNull()
    }

    // AC-18
    @Test
    fun aRegisterResponseWithoutADecisionClearsTheOldCache() = runTest {
        val (repository, session) = repositoryReturning(HttpStatusCode.OK, successBody)
        session.saveConsent("granted", 1)

        repository.register("user@example.com", "Password1", consent = null)

        assertThat(session.getConsentStatus()).isNull()
        assertThat(session.getConsentTextVersion()).isNull()
    }

    // AC-18, AC-27
    @Test
    fun logoutRemovesTheCachedDecision() = runTest {
        val (repository, session) = repositoryReturning(HttpStatusCode.OK, registerBodyWithConsent)
        repository.register("user@example.com", "Password1", ConsentChoice(true, 2, "tr"))

        repository.logout()

        assertThat(session.getConsentStatus()).isNull()
        assertThat(session.getConsentTextVersion()).isNull()
    }

    // AC-3
    @Test
    fun theObserverSeesTheCachedDecisionWhenItHearsAboutTheRegistration() = runTest {
        val observer = FakeSessionObserver()
        val (repository, session) = repositoryReturning(
            HttpStatusCode.OK,
            registerBodyWithConsent,
            observers = listOf(observer)
        )
        var statusSeenByObserver: String? = null
        observer.onEvent = { statusSeenByObserver = session.getConsentStatus() }

        repository.register("user@example.com", "Password1", ConsentChoice(true, 2, "tr"))

        assertThat(statusSeenByObserver).isEqualTo("granted")
    }
}
