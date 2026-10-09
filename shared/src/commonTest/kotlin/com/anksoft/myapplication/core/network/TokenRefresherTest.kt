package com.anksoft.myapplication.core.network

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.anksoft.kmpdemo.contract.auth.AuthResponseDto
import com.anksoft.kmpdemo.contract.auth.RefreshTokenRequestDto
import com.anksoft.kmpdemo.contract.auth.UserDto
import com.anksoft.myapplication.core.config.TestAppConfigs
import com.anksoft.myapplication.core.logging.NoOpLogger
import com.anksoft.myapplication.core.session.FakeSessionObserver
import com.anksoft.myapplication.core.session.SessionExpiry
import com.anksoft.myapplication.core.session.SignOutReason
import com.anksoft.myapplication.core.storage.SessionManager
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * Covers [TokenRefresher] directly and through the real [createHttpClient] with a MockEngine.
 * Access token `access-2` is the only one the fake backend accepts on protected paths.
 */
class TokenRefresherTest {

    private val session = SessionManager(MapSettings()).apply {
        saveToken("access-1")
        saveRefreshToken("refresh-1")
    }
    private val observer = FakeSessionObserver()
    private val expiry = SessionExpiry(session, lazy { listOf(observer) })
    private var expiredCount = 0
    private val refresher = TokenRefresher(session, NoOpLogger, onSessionExpired = { onExpired() })

    private fun onExpired() {
        expiredCount++
        expiry.expire()
    }

    @AfterTest
    fun tearDown() {
        client.close()
    }

    // region TokenRefresher on its own

    // AC-28
    @Test
    fun successfulRefreshStoresTheNewTokensAndReturnsThem() = runTest {
        val tokens = refresher.refresh(staleRefreshToken = "refresh-1") {
            responseOf(HttpStatusCode.OK, authJson("access-2", "refresh-2"))
        }

        assertThat(tokens?.accessToken).isEqualTo("access-2")
        assertThat(tokens?.refreshToken).isEqualTo("refresh-2")
        assertThat(session.getToken()).isEqualTo("access-2")
        assertThat(session.getRefreshToken()).isEqualTo("refresh-2")
    }

    // AC-28
    @Test
    fun newTokensAreStoredBeforeTheyAreReturned() = runTest {
        var storedWhileRequestWasOpen: String? = null

        refresher.refresh(staleRefreshToken = "refresh-1") {
            storedWhileRequestWasOpen = session.getToken()
            responseOf(HttpStatusCode.OK, authJson("access-2", "refresh-2"))
        }

        // The old pair is still stored while the request runs; the new pair appears only after.
        assertThat(storedWhileRequestWasOpen).isEqualTo("access-1")
        assertThat(session.getToken()).isEqualTo("access-2")
    }

    // AC-28
    @Test
    fun callerHoldingARotatedRefreshTokenGetsTheStoredTokensWithoutANetworkCall() = runTest {
        session.saveToken("access-2")
        session.saveRefreshToken("refresh-2")
        var networkCalls = 0

        val tokens = refresher.refresh(staleRefreshToken = "refresh-1") {
            networkCalls++
            responseOf(HttpStatusCode.OK, authJson("access-3", "refresh-3"))
        }

        assertThat(networkCalls).isEqualTo(0)
        assertThat(tokens?.accessToken).isEqualTo("access-2")
        assertThat(tokens?.refreshToken).isEqualTo("refresh-2")
    }

    // AC-28
    @Test
    fun requestSentWithATokenThisSessionReplacedReusesTheStoredTokens() = runTest {
        refresher.refresh(staleRefreshToken = "refresh-1") {
            responseOf(HttpStatusCode.OK, authJson("access-2", "refresh-2"))
        }
        var networkCalls = 0

        val tokens = refresher.refresh(staleRefreshToken = "refresh-1", failedAccessToken = "access-1") {
            networkCalls++
            responseOf(HttpStatusCode.OK, authJson("access-3", "refresh-3"))
        }

        assertThat(networkCalls).isEqualTo(0)
        assertThat(tokens?.accessToken).isEqualTo("access-2")
        assertThat(tokens?.refreshToken).isEqualTo("refresh-2")
    }

    // AC-18
    @Test
    fun requestSentUnderAnotherAccountIsNotRetriedWithTheCurrentOnesTokens() = runTest {
        session.saveToken("access-b")
        session.saveRefreshToken("refresh-b")
        var networkCalls = 0

        val tokens = refresher.refresh(staleRefreshToken = "refresh-b", failedAccessToken = "access-a") {
            networkCalls++
            responseOf(HttpStatusCode.OK, authJson("access-3", "refresh-3"))
        }

        assertThat(tokens).isNull()
        assertThat(networkCalls).isEqualTo(0)
        assertThat(session.getToken()).isEqualTo("access-b")
    }

    // AC-18
    @Test
    fun tokensOfAnEarlierSessionLineageAreForgottenAfterANewSignIn() = runTest {
        refresher.refresh(staleRefreshToken = "refresh-1") {
            responseOf(HttpStatusCode.OK, authJson("access-2", "refresh-2"))
        }
        // Another account signs in; the old account's request fails late.
        session.saveToken("access-b")
        session.saveRefreshToken("refresh-b")

        val tokens = refresher.refresh(staleRefreshToken = "refresh-1", failedAccessToken = "access-1") {
            responseOf(HttpStatusCode.OK, authJson("access-3", "refresh-3"))
        }

        assertThat(tokens).isNull()
        assertThat(session.getToken()).isEqualTo("access-b")
    }

    // AC-28
    @Test
    fun twoCallersWithTheSameStaleTokenCauseOneRefreshRequest() = runTest {
        val sentRefreshTokens = mutableListOf<String>()

        val results = listOf(
            async {
                refresher.refresh(staleRefreshToken = "refresh-1") { refreshToken ->
                    sentRefreshTokens += refreshToken
                    responseOf(HttpStatusCode.OK, authJson("access-2", "refresh-2"))
                }
            },
            async {
                refresher.refresh(staleRefreshToken = "refresh-1") { refreshToken ->
                    sentRefreshTokens += refreshToken
                    responseOf(HttpStatusCode.OK, authJson("access-2", "refresh-2"))
                }
            }
        ).awaitAll()

        assertThat(sentRefreshTokens).isEqualTo(listOf("refresh-1"))
        assertThat(results.map { it?.accessToken }).isEqualTo(listOf("access-2", "access-2"))
    }

    // AC-28
    @Test
    fun noStoredSessionMeansNoRefreshRequest() = runTest {
        session.clear()
        var networkCalls = 0

        val tokens = refresher.refresh(staleRefreshToken = "refresh-1") {
            networkCalls++
            responseOf(HttpStatusCode.OK, authJson("access-2", "refresh-2"))
        }

        assertThat(tokens).isNull()
        assertThat(networkCalls).isEqualTo(0)
        assertThat(expiredCount).isEqualTo(0)
    }

    // AC-28
    @Test
    fun responseWithoutARefreshTokenKeepsTheStoredOne() = runTest {
        val tokens = refresher.refresh(staleRefreshToken = "refresh-1") {
            responseOf(HttpStatusCode.OK, authJson("access-2", refreshToken = null))
        }

        assertThat(tokens?.refreshToken).isEqualTo("refresh-1")
        assertThat(session.getRefreshToken()).isEqualTo("refresh-1")
        assertThat(session.getToken()).isEqualTo("access-2")
    }

    // AC-28
    @Test
    fun rejectedRefreshTokenExpiresTheSessionOnce() = runTest {
        val tokens = refresher.refresh(staleRefreshToken = "refresh-1") {
            responseOf(HttpStatusCode.Unauthorized)
        }

        assertThat(tokens).isNull()
        assertThat(expiredCount).isEqualTo(1)
        assertThat(session.getToken()).isNull()
        assertThat(session.getRefreshToken()).isNull()
        assertThat(observer.events).isEqualTo(listOf(FakeSessionObserver.Event.SignedOut(SignOutReason.EXPIRED)))
    }

    // AC-28
    @Test
    fun serverErrorKeepsTheSession() = runTest {
        val tokens = refresher.refresh(staleRefreshToken = "refresh-1") {
            responseOf(HttpStatusCode.InternalServerError)
        }

        assertThat(tokens).isNull()
        assertThat(expiredCount).isEqualTo(0)
        assertThat(session.getToken()).isEqualTo("access-1")
        assertThat(session.getRefreshToken()).isEqualTo("refresh-1")
    }

    // AC-28
    @Test
    fun sessionReplacedWhileRefreshingIsLeftAlone() = runTest {
        val tokens = refresher.refresh(staleRefreshToken = "refresh-1") {
            // The user logged out and signed in again while the request was in flight.
            session.clear()
            session.saveToken("access-b")
            session.saveRefreshToken("refresh-b")
            responseOf(HttpStatusCode.OK, authJson("access-2", "refresh-2"))
        }

        assertThat(tokens).isNull()
        assertThat(session.getToken()).isEqualTo("access-b")
        assertThat(session.getRefreshToken()).isEqualTo("refresh-b")
    }

    // AC-28
    @Test
    fun cancelledCallerStillStoresTheRotatedTokens() = runTest {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val job = launch {
            refresher.refresh(staleRefreshToken = "refresh-1") {
                entered.complete(Unit)
                gate.await()
                responseOf(HttpStatusCode.OK, authJson("access-2", "refresh-2"))
            }
        }
        entered.await()

        job.cancel()
        gate.complete(Unit)
        job.join()

        // The server consumed refresh-1, so the store must not keep it.
        assertThat(session.getRefreshToken()).isEqualTo("refresh-2")
        assertThat(session.getToken()).isEqualTo("access-2")
    }

    // endregion

    // region Through the real HTTP client

    // Written from MockEngine threads, always under recordLock.
    private val recordLock = Mutex()
    private val refreshTokensReceived = mutableListOf<String>()
    private val protectedRequestTokens = mutableListOf<String?>()
    private val authPathRequests = mutableListOf<String>()
    private val firstTokenArrivals = mutableListOf<String>()
    private val refreshEntered = CompletableDeferred<Unit>()
    private var refreshGate: CompletableDeferred<Unit>? = null
    private var hold = Hold.NONE
    private val bothArrived = CompletableDeferred<Unit>()
    private val lateRequestArrived = CompletableDeferred<Unit>()
    private val afterFirstRetry = CompletableDeferred<Unit>()
    private var storedTokenWhenRetried: String? = null
    private var refreshResponse: MockRequestHandleScope.() -> HttpResponseData = {
        respond(authJson("access-2", "refresh-2"), HttpStatusCode.OK, jsonHeaders)
    }

    private val engine = MockEngine { request ->
        val path = request.url.encodedPath
        when {
            path.endsWith("auth/refresh") -> {
                val body = request.body.toByteArray().decodeToString()
                val refreshToken = appJson.decodeFromString<RefreshTokenRequestDto>(body).refreshToken
                recordLock.withLock { refreshTokensReceived += refreshToken }
                refreshEntered.complete(Unit)
                refreshGate?.await()
                refreshResponse(this)
            }

            path.endsWith("auth/login") || path.endsWith("auth/register") -> {
                recordLock.withLock { authPathRequests += path }
                unauthorized()
            }

            else -> {
                val authorization = request.headers[HttpHeaders.Authorization]
                recordLock.withLock { protectedRequestTokens += authorization }
                holdUntilTestAllowsAnswer(path, authorization)
                if (authorization == "Bearer access-2") {
                    afterFirstRetry.complete(Unit)
                    storedTokenWhenRetried = session.getToken()
                    respond("ok", HttpStatusCode.OK)
                } else {
                    unauthorized()
                }
            }
        }
    }

    private enum class Hold {
        NONE,

        /** Both requests carrying the first token wait for each other, so their 401s overlap. */
        BOTH_ARRIVE,

        /** `data/one` is answered at once; `data/late` is answered only after `data/one` was retried. */
        LATE_ANSWER
    }

    /** Lets a test decide exactly when the 401 for a request carrying the first token is sent. */
    private suspend fun holdUntilTestAllowsAnswer(path: String, authorization: String?) {
        if (authorization != "Bearer access-1") return
        when (hold) {
            Hold.NONE -> Unit
            Hold.BOTH_ARRIVE -> {
                val arrivals = recordLock.withLock {
                    firstTokenArrivals += path
                    firstTokenArrivals.size
                }
                if (arrivals == 2) bothArrived.complete(Unit)
                bothArrived.await()
            }
            Hold.LATE_ANSWER -> if (path.endsWith("data/late")) {
                lateRequestArrived.complete(Unit)
                afterFirstRetry.await()
            } else {
                lateRequestArrived.await()
            }
        }
    }

    private val client = createHttpClient(
        engine = engine,
        config = TestAppConfigs.remote("https://api.test/"),
        sessionManager = session,
        logger = NoOpLogger,
        onSessionExpired = { onExpired() }
    )

    // AC-28
    @Test
    fun expiredAccessTokenIsRefreshedAndTheRequestIsRetriedWithTheNewToken() = runTest {
        val response = client.get("data")

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(refreshTokensReceived).isEqualTo(listOf("refresh-1"))
        assertThat(protectedRequestTokens).isEqualTo(listOf("Bearer access-1", "Bearer access-2"))
    }

    // AC-28
    @Test
    fun newTokensAreInTheSessionStoreWhenTheRetryArrives() = runTest {
        client.get("data")

        assertThat(storedTokenWhenRetried).isEqualTo("access-2")
        assertThat(session.getRefreshToken()).isEqualTo("refresh-2")
    }

    // AC-28
    @Test
    fun twoOverlappingUnauthorizedRequestsShareOneRefresh() = runTest {
        hold = Hold.BOTH_ARRIVE

        val responses = listOf(
            async { client.get("data/one") },
            async { client.get("data/two") }
        ).awaitAll()

        assertThat(responses.map { it.status }).isEqualTo(listOf(HttpStatusCode.OK, HttpStatusCode.OK))
        assertThat(refreshTokensReceived).isEqualTo(listOf("refresh-1"))
        // Both were sent with the old token first and retried with the new one.
        assertThat(protectedRequestTokens.filterNotNull().sorted())
            .isEqualTo(listOf("Bearer access-1", "Bearer access-1", "Bearer access-2", "Bearer access-2"))
    }

    // AC-28
    @Test
    fun unauthorizedAnswerThatArrivesAfterTheRefreshIsRetriedWithoutAnotherRefresh() = runTest {
        hold = Hold.LATE_ANSWER

        val responses = listOf(
            async { client.get("data/one") },
            async { client.get("data/late") }
        ).awaitAll()

        assertThat(responses.map { it.status }).isEqualTo(listOf(HttpStatusCode.OK, HttpStatusCode.OK))
        assertThat(refreshTokensReceived).isEqualTo(listOf("refresh-1"))
    }

    // AC-28
    @Test
    fun leavingScreenDuringRefreshStillStoresTheRotatedTokens() = runTest {
        val gate = CompletableDeferred<Unit>()
        refreshGate = gate
        val job = launch { client.get("data") }
        refreshEntered.await()

        job.cancel()
        gate.complete(Unit)
        job.join()

        assertThat(session.getRefreshToken()).isEqualTo("refresh-2")
        assertThat(session.getToken()).isEqualTo("access-2")
    }

    // AC-28
    @Test
    fun rejectedRefreshEndsTheSessionWithoutRetryingOrLooping() = runTest {
        refreshResponse = { unauthorized() }

        val response = client.get("data")

        assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(refreshTokensReceived).hasSize(1)
        assertThat(protectedRequestTokens).hasSize(1)
        assertThat(expiredCount).isEqualTo(1)
        assertThat(session.getToken()).isNull()
        assertThat(observer.events).isEqualTo(listOf(FakeSessionObserver.Event.SignedOut(SignOutReason.EXPIRED)))
        assertThat(expiry.consumeExpiredNotice()).isTrue()
    }

    // AC-28
    @Test
    fun refreshServerErrorKeepsTheSessionAndSurfacesTheUnauthorizedResponse() = runTest {
        refreshResponse = { respond("", HttpStatusCode.InternalServerError) }

        val response = client.get("data")

        assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(expiredCount).isEqualTo(0)
        assertThat(session.getToken()).isEqualTo("access-1")
        assertThat(session.getRefreshToken()).isEqualTo("refresh-1")
        assertThat(observer.events).isEmpty()
    }

    // AC-28
    @Test
    fun unauthorizedLoginOrRegisterNeverStartsARefresh() = runTest {
        val login = client.post("auth/login")
        val register = client.post("auth/register")

        assertThat(login.status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(register.status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(authPathRequests).hasSize(2)
        assertThat(refreshTokensReceived).isEmpty()
        assertThat(session.getToken()).isNotNull()
        assertThat(expiredCount).isEqualTo(0)
    }

    // endregion

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    private fun MockRequestHandleScope.unauthorized(): HttpResponseData =
        respond("", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.WWWAuthenticate, "Bearer"))

    private fun authJson(accessToken: String, refreshToken: String?): String =
        appJson.encodeToString(
            AuthResponseDto.serializer(),
            AuthResponseDto(accessToken, refreshToken, UserDto(id = "u1", email = "user@example.com"))
        )

    /** A real response, as the refresh request would produce, so safeCall can decode it. */
    private suspend fun responseOf(status: HttpStatusCode, body: String = ""): HttpResponse {
        val engine = MockEngine { respond(body, status, jsonHeaders) }
        val responseClient = HttpClient(engine) { install(ContentNegotiation) { json(appJson) } }
        try {
            return responseClient.get("https://refresh.test/")
        } finally {
            responseClient.close()
        }
    }
}
