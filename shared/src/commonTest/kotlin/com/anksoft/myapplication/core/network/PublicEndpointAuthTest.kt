package com.anksoft.myapplication.core.network

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.anksoft.kmpdemo.contract.consent.ConsentPaths
import com.anksoft.myapplication.core.config.TestAppConfigs
import com.anksoft.myapplication.core.logging.NoOpLogger
import com.anksoft.myapplication.core.storage.SessionManager
import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/**
 * The real [createHttpClient] against a MockEngine, with a signed-in session: public endpoints must
 * never see the stored token, and a 401 from them must not start a refresh or a token-bearing resend.
 */
class PublicEndpointAuthTest {

    private val session = SessionManager(MapSettings()).apply {
        saveToken("access-1")
        saveRefreshToken("refresh-1")
    }
    private var expiredCount = 0

    /** Path and Authorization header of every request that reached the engine, in order. */
    private val received = mutableListOf<Pair<String, String?>>()
    private var publicAnswer = HttpStatusCode.OK

    private val engine = MockEngine { request ->
        val path = request.url.encodedPath
        received += path to request.headers[HttpHeaders.Authorization]
        when {
            path.endsWith(ConsentPaths.TEXTS) ->
                respond("", publicAnswer, headersOf(HttpHeaders.WWWAuthenticate, "Bearer"))
            else -> respondOk()
        }
    }

    private val client = createHttpClient(
        engine = engine,
        config = TestAppConfigs.remote("https://api.test/api/v1/"),
        sessionManager = session,
        logger = NoOpLogger,
        onSessionExpired = { expiredCount++ },
    )

    // AC-21
    @Test
    fun consentTextsRequestCarriesNoAuthorizationHeaderEvenWithASession() = runTest {
        client.get(ConsentPaths.TEXTS)

        assertThat(received).containsExactly("/api/v1/${ConsentPaths.TEXTS}" to null)
    }

    // AC-21 (regression: protected paths keep the token)
    @Test
    fun protectedRequestStillCarriesTheBearerToken() = runTest {
        client.get("auth/me")

        assertThat(received).containsExactly("/api/v1/auth/me" to "Bearer access-1")
    }

    // AC-6, AC-14
    @Test
    fun unauthorizedAnswerFromAPublicPathIsNotRetriedAndLeavesTheSessionAlone() = runTest {
        publicAnswer = HttpStatusCode.Unauthorized

        val response = client.get(ConsentPaths.TEXTS)

        assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
        // One request only: no refresh call and no resend carrying the token.
        assertThat(received.map { it.first }).containsExactly("/api/v1/${ConsentPaths.TEXTS}")
        assertThat(received.map { it.second }).containsExactly(null)
        assertThat(expiredCount).isEqualTo(0)
        assertThat(session.getToken()).isEqualTo("access-1")
        assertThat(session.getRefreshToken()).isEqualTo("refresh-1")
    }

    // AC-21
    @Test
    fun publicPathMatchingIsBySuffixSoThePrefixOfTheBaseUrlDoesNotMatter() {
        assertThat(isPublicPath("/api/v1/${ConsentPaths.TEXTS}")).isTrue()
        assertThat(isPublicPath("/${ConsentPaths.TEXTS}")).isTrue()
        assertThat(isPublicPath("/api/v1/auth/me")).isFalse()
        assertThat(isPublicPath("")).isFalse()
    }

    // AC-21 (a repeated public request, after the first one, still never carries a token)
    @Test
    fun repeatedPublicRequestsNeverCarryAToken() = runTest {
        client.get(ConsentPaths.TEXTS)
        client.get(ConsentPaths.TEXTS)

        assertThat(received.mapNotNull { it.second }).isEmpty()
        assertThat(received.firstOrNull()?.second).isNull()
    }
}
