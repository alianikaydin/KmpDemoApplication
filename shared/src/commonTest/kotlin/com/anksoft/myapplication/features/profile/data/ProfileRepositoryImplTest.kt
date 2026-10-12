package com.anksoft.myapplication.features.profile.data

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.anksoft.myapplication.core.config.TestAppConfigs
import com.anksoft.myapplication.core.logging.LogSeverity
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.NoOpLogger
import com.anksoft.myapplication.core.logging.recordingLogger
import com.anksoft.myapplication.core.network.createHttpClient
import com.anksoft.myapplication.core.session.SignOutReason
import com.anksoft.myapplication.core.storage.SessionManager
import com.anksoft.myapplication.features.profile.FakeProfileLocalDataSource
import com.anksoft.myapplication.features.profile.data.datasource.ProfileRemoteDataSource
import com.anksoft.myapplication.features.profile.data.repository.ProfileRepositoryImpl
import com.anksoft.myapplication.features.profile.domain.model.AccountProfile
import com.anksoft.myapplication.features.profile.domain.model.ProfileState
import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

private typealias MockHandler = suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData

/**
 * Runs the real HTTP client stack on a mock engine and the real session store; the disk is a fake.
 * Answers from the engine arrive on another thread, so those waits are in real time (the same
 * approach as KoinSessionWiringTest); everything local runs on the virtual test clock.
 */
class ProfileRepositoryImplTest {

    private val alice = AccountProfile(userId = "u1", email = "user@example.com", name = "Alice")
    private val session = SessionManager(MapSettings())
    private val local = FakeProfileLocalDataSource()

    private val requestPaths = mutableListOf<String>()
    private var handler: MockHandler = { json(HttpStatusCode.OK, userJson()) }

    private val client = createHttpClient(
        engine = MockEngine { request ->
            requestPaths += request.url.encodedPath
            handler(this, request)
        },
        config = TestAppConfigs.remote("https://api.test/api/v1/"),
        sessionManager = session,
        logger = NoOpLogger,
    )
    private val recorded = recordingLogger()
    private val logger = recorded.first
    private val log = recorded.second

    @AfterTest
    fun tearDown() {
        client.close()
    }

    private fun MockRequestHandleScope.json(status: HttpStatusCode, body: String): HttpResponseData =
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )

    private fun userJson(id: String = "u1", email: String = "user@example.com", name: String? = "Alice"): String =
        if (name == null) {
            """{"id":"$id","email":"$email"}"""
        } else {
            """{"id":"$id","email":"$email","name":"$name"}"""
        }

    private fun signIn(userId: String = "u1") {
        session.saveToken("access-$userId")
        session.saveRefreshToken("refresh-$userId")
        session.saveUserId(userId)
    }

    private fun signOut(repository: ProfileRepositoryImpl, reason: SignOutReason = SignOutReason.USER_LOGOUT) {
        session.clear()
        repository.onSignedOut(reason)
    }

    private fun TestScope.newRepository(): ProfileRepositoryImpl =
        ProfileRepositoryImpl(ProfileRemoteDataSource(client, logger), local, session, backgroundScope, logger)

    private suspend fun ProfileRepositoryImpl.awaitState(matches: (ProfileState) -> Boolean): ProfileState =
        withContext(Dispatchers.Default) { withTimeout(10.seconds) { state.first(matches) } }

    private suspend fun <T> awaitReal(deferred: CompletableDeferred<T>): T =
        withContext(Dispatchers.Default) { withTimeout(10.seconds) { deferred.await() } }

    private fun assertLogsHoldNoPersonalData() {
        assertThat(log.entries.isNotEmpty()).isTrue()
        log.entries.forEach { entry ->
            assertThat(entry.message).doesNotContain("u1")
            assertThat(entry.message).doesNotContain("u2")
            assertThat(entry.message).doesNotContain("example.com")
        }
    }

    private suspend fun TestScope.assertStaleAfter(answer: MockHandler) {
        signIn()
        local.seed(alice)
        handler = answer
        val repository = newRepository()
        runCurrent()

        repository.refresh()
        val state = repository.awaitState { it is ProfileState.Available && it.isStale }

        assertThat(state).isEqualTo(ProfileState.Available(alice, isStale = true))
        assertThat(local.rows["u1"]).isEqualTo(alice)
        assertThat(local.replaceCalls).isEmpty()
    }

    // AC-10
    @Test
    fun withoutASessionTheStateIsSignedOutAndEveryStoredRowIsDeleted() = runTest {
        local.seed(alice)

        val repository = newRepository()
        runCurrent()

        assertThat(repository.state.value).isEqualTo(ProfileState.SignedOut)
        assertThat(local.calls).contains("retainOnly(null)")
        assertThat(local.rows).isEqualTo(emptyMap<String, AccountProfile>())
    }

    // AC-3
    @Test
    fun aCachedProfileIsShownBeforeTheRequestAndLoadingIsNeverShown() = runTest {
        signIn()
        local.seed(alice.copy(name = "Old name"))
        val repository = newRepository()
        runCurrent()

        repository.state.test {
            assertThat(awaitItem()).isEqualTo(ProfileState.Available(alice.copy(name = "Old name"), isStale = false))
            repository.refresh()
            assertThat(awaitItem()).isEqualTo(ProfileState.Available(alice, isStale = false))
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(requestPaths).hasSize(1)
        assertThat(local.rows["u1"]).isEqualTo(alice)
    }

    // AC-4
    @Test
    fun withoutACachedProfileLoadingIsShownUntilTheResponseArrives() = runTest {
        signIn()
        val gate = CompletableDeferred<Unit>()
        val arrived = CompletableDeferred<Unit>()
        handler = {
            arrived.complete(Unit)
            gate.await()
            json(HttpStatusCode.OK, userJson())
        }
        val repository = newRepository()
        runCurrent()

        repository.refresh()
        awaitReal(arrived)
        assertThat(repository.state.value).isEqualTo(ProfileState.Loading)
        gate.complete(Unit)
        val state = repository.awaitState { it is ProfileState.Available }

        assertThat(state).isEqualTo(ProfileState.Available(alice, isStale = false))
        assertThat(local.rows["u1"]).isEqualTo(alice)
    }

    // AC-5
    @Test
    fun aCachedProfileIsStaleWhenThereIsNoNetwork() = runTest {
        assertStaleAfter { error("network down") }
    }

    // AC-5
    @Test
    fun aCachedProfileIsStaleWhenTheRequestTimesOut() = runTest {
        assertStaleAfter { json(HttpStatusCode.RequestTimeout, "") }
    }

    // AC-5
    @Test
    fun aCachedProfileIsStaleWhenTheServerFails() = runTest {
        assertStaleAfter { json(HttpStatusCode.InternalServerError, "") }
    }

    // AC-7
    @Test
    fun withoutACachedProfileAFailedRefreshIsUnavailable() = runTest {
        signIn()
        handler = { json(HttpStatusCode.InternalServerError, "") }
        val repository = newRepository()
        runCurrent()

        repository.refresh()
        val state = repository.awaitState { it == ProfileState.Unavailable }

        assertThat(state).isEqualTo(ProfileState.Unavailable)
    }

    // AC-8
    @Test
    fun aRetryAfterAFailureFromAnEmptyCacheShowsTheProfileWhenTheNetworkIsBack() = runTest {
        signIn()
        handler = { json(HttpStatusCode.InternalServerError, "") }
        val repository = newRepository()
        runCurrent()
        repository.refresh()
        repository.awaitState { it == ProfileState.Unavailable }

        handler = { json(HttpStatusCode.OK, userJson()) }
        repository.refresh()
        val state = repository.awaitState { it is ProfileState.Available }

        assertThat(state).isEqualTo(ProfileState.Available(alice, isStale = false))
    }

    // AC-8
    @Test
    fun aRetryAfterAFailureWithACachedProfileClearsTheStaleNote() = runTest {
        signIn()
        local.seed(alice.copy(name = "Old name"))
        handler = { error("network down") }
        val repository = newRepository()
        runCurrent()
        repository.refresh()
        repository.awaitState { it is ProfileState.Available && it.isStale }

        handler = { json(HttpStatusCode.OK, userJson()) }
        repository.refresh()
        val state = repository.awaitState { it is ProfileState.Available && !it.isStale && it.profile == alice }

        assertThat(state).isEqualTo(ProfileState.Available(alice, isStale = false))
        assertThat(local.rows["u1"]).isEqualTo(alice)
    }

    // AC-18
    @Test
    fun aSecondRefreshWhileOneIsRunningSendsNoSecondRequest() = runTest {
        signIn()
        val gate = CompletableDeferred<Unit>()
        val arrived = CompletableDeferred<Unit>()
        handler = {
            arrived.complete(Unit)
            gate.await()
            json(HttpStatusCode.OK, userJson())
        }
        val repository = newRepository()
        runCurrent()

        repository.refresh()
        awaitReal(arrived)
        repository.refresh()
        repository.refresh()
        gate.complete(Unit)
        repository.awaitState { it is ProfileState.Available }

        assertThat(requestPaths).hasSize(1)
        assertThat(local.replaceCalls).hasSize(1)
    }

    @Test
    fun refreshDoesNothingWithoutASession() = runTest {
        val repository = newRepository()
        runCurrent()

        repository.refresh()
        runCurrent()

        assertThat(requestPaths).isEmpty()
        assertThat(repository.state.value).isEqualTo(ProfileState.SignedOut)
    }

    // AC-10
    @Test
    fun loggingOutShowsSignedOutAtOnceAndDeletesTheStoredRow() = runTest {
        signIn()
        local.seed(alice)
        val repository = newRepository()
        runCurrent()
        assertThat(repository.state.value).isEqualTo(ProfileState.Available(alice, isStale = false))

        signOut(repository, SignOutReason.USER_LOGOUT)

        assertThat(repository.state.value).isEqualTo(ProfileState.SignedOut)
        runCurrent()
        assertThat(local.rows).isEqualTo(emptyMap<String, AccountProfile>())
    }

    // AC-11
    @Test
    fun anExpiredSessionShowsSignedOutAtOnceAndDeletesTheStoredRow() = runTest {
        signIn()
        local.seed(alice)
        val repository = newRepository()
        runCurrent()

        signOut(repository, SignOutReason.EXPIRED)

        assertThat(repository.state.value).isEqualTo(ProfileState.SignedOut)
        runCurrent()
        assertThat(local.rows).isEqualTo(emptyMap<String, AccountProfile>())
    }

    // AC-13
    @Test
    fun aResponseThatIsStillOnItsWayWhenTheUserLogsOutIsNeverStored() = runTest {
        signIn()
        val gate = CompletableDeferred<Unit>()
        val arrived = CompletableDeferred<Unit>()
        handler = {
            arrived.complete(Unit)
            gate.await()
            json(HttpStatusCode.OK, userJson())
        }
        val repository = newRepository()
        runCurrent()
        repository.refresh()
        awaitReal(arrived)

        signOut(repository)
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertThat(repository.state.value).isEqualTo(ProfileState.SignedOut)
        assertThat(local.replaceCalls).isEmpty()
        assertThat(local.rows).isEqualTo(emptyMap<String, AccountProfile>())
    }

    // AC-13
    @Test
    fun aProfileSavedByAnEarlierSessionIsDroppedEvenWhenTheSameAccountSignedInAgain() = runTest {
        signIn()
        val gate = CompletableDeferred<Unit>()
        // The first write of the repository (the cold-start cleanup) holds the database queue.
        local.writeGate = gate
        val repository = newRepository()
        runCurrent()
        val saving = launch { repository.saveSignedInProfile(alice) }
        runCurrent()

        signOut(repository)
        signIn()
        repository.onSignedIn()
        gate.complete(Unit)
        runCurrent()
        saving.join()

        assertThat(local.replaceCalls).isEmpty()
        assertThat(repository.state.value).isEqualTo(ProfileState.Loading)
    }

    // AC-13
    @Test
    fun aWriteThatIsRunningWhenTheUserLogsOutIsFollowedByTheDeletion() = runTest {
        signIn()
        val repository = newRepository()
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        local.writeGate = gate
        val saving = launch { repository.saveSignedInProfile(alice) }
        runCurrent()
        assertThat(local.replaceCalls).hasSize(1)

        signOut(repository)
        gate.complete(Unit)
        runCurrent()
        saving.join()

        assertThat(local.rows).isEqualTo(emptyMap<String, AccountProfile>())
        assertThat(repository.state.value).isEqualTo(ProfileState.SignedOut)
    }

    // AC-12
    @Test
    fun aResponseForAnotherAccountIsNotStoredAndCountsAsAFailure() = runTest {
        signIn()
        local.seed(alice)
        handler = { json(HttpStatusCode.OK, userJson(id = "u2", email = "other@example.com", name = "Bob")) }
        val repository = newRepository()
        runCurrent()

        repository.refresh()
        val state = repository.awaitState { it is ProfileState.Available && it.isStale }

        assertThat(state).isEqualTo(ProfileState.Available(alice, isStale = true))
        assertThat(local.replaceCalls).isEmpty()
        assertThat(local.rows["u1"]).isEqualTo(alice)
        val warnings = log.entries.filter { it.tag == LogTags.PROFILE && it.severity == LogSeverity.WARN }
        assertThat(warnings.map { it.message }).contains("profile response dropped: account mismatch")
        assertLogsHoldNoPersonalData()
    }

    // AC-12
    @Test
    fun aResponseForAnotherAccountWithoutACacheIsUnavailable() = runTest {
        signIn()
        handler = { json(HttpStatusCode.OK, userJson(id = "u2", email = "other@example.com", name = "Bob")) }
        val repository = newRepository()
        runCurrent()

        repository.refresh()
        val state = repository.awaitState { it == ProfileState.Unavailable }

        assertThat(state).isEqualTo(ProfileState.Unavailable)
        assertThat(local.replaceCalls).isEmpty()
    }

    // AC-12
    @Test
    fun anotherAccountsStoredRowIsNeverShownAndIsDeletedAtStart() = runTest {
        signIn("u2")
        local.seed(alice)
        val repository = newRepository()
        val shown = mutableListOf<ProfileState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.state.collect { shown += it } }

        runCurrent()
        handler = { json(HttpStatusCode.OK, userJson(id = "u2", email = "bob@example.com", name = "Bob")) }
        repository.refresh()
        repository.awaitState { it is ProfileState.Available }

        val bob = AccountProfile(userId = "u2", email = "bob@example.com", name = "Bob")
        assertThat(shown.filterIsInstance<ProfileState.Available>().map { it.profile }).isEqualTo(listOf(bob))
        assertThat(local.rows).isEqualTo(mapOf("u2" to bob))
    }

    // AC-17
    @Test
    fun aFailingReadFallsBackToTheNetwork() = runTest {
        signIn()
        local.seed(alice.copy(name = "Old name"))
        local.failReads = true
        val repository = newRepository()
        runCurrent()
        assertThat(repository.state.value).isEqualTo(ProfileState.Loading)

        repository.refresh()
        val state = repository.awaitState { it is ProfileState.Available }

        assertThat(state).isEqualTo(ProfileState.Available(alice, isStale = false))
    }

    // AC-17
    @Test
    fun aFailingWriteStillShowsTheFetchedProfileFromMemory() = runTest {
        signIn()
        local.failWrites = true
        val repository = newRepository()
        runCurrent()

        repository.refresh()
        val state = repository.awaitState { it is ProfileState.Available }

        assertThat(state).isEqualTo(ProfileState.Available(alice, isStale = false))
        assertThat(local.rows).isEqualTo(emptyMap<String, AccountProfile>())
    }

    // AC-2
    @Test
    fun aSignInProfileIsShownAtOnceAndStoredWithoutARequest() = runTest {
        val repository = newRepository()
        runCurrent()
        signIn()
        repository.onSignedIn()
        assertThat(repository.state.value).isEqualTo(ProfileState.Loading)

        repository.saveSignedInProfile(alice)

        assertThat(repository.state.value).isEqualTo(ProfileState.Available(alice, isStale = false))
        assertThat(local.rows["u1"]).isEqualTo(alice)
        assertThat(requestPaths).isEmpty()
    }

    // AC-13
    @Test
    fun aSignInProfileThatArrivesAfterALogoutIsNotStored() = runTest {
        signIn()
        val repository = newRepository()
        runCurrent()
        signOut(repository)
        runCurrent()

        repository.saveSignedInProfile(alice)

        assertThat(local.replaceCalls).isEmpty()
        assertThat(repository.state.value).isEqualTo(ProfileState.SignedOut)
    }

    // AC-18
    @Test
    fun theResultIsStoredOnceEvenIfTheCallerOfRefreshIsCancelled() = runTest {
        signIn()
        val repository = newRepository()
        runCurrent()
        val screen = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())

        screen.launch { repository.refresh() }
        screen.coroutineContext[Job]?.cancel()
        val state = repository.awaitState { it is ProfileState.Available }

        assertThat(state).isEqualTo(ProfileState.Available(alice, isStale = false))
        assertThat(local.replaceCalls).hasSize(1)
    }

    // AC-16
    @Test
    fun worksWithoutAnyConsentOrAnalyticsDependency() = runTest {
        // The constructor takes no consent manager and no tracker, so no consent state can matter.
        signIn()
        val repository = newRepository()
        runCurrent()

        repository.refresh()
        val state = repository.awaitState { it is ProfileState.Available }

        assertThat(state).isInstanceOf<ProfileState.Available>()
    }

    // AC-15
    @Test
    fun logLinesOfAFailedRefreshNeverContainTheUserIdOrTheEmail() = runTest {
        signIn()
        local.seed(alice)
        handler = { json(HttpStatusCode.InternalServerError, "") }
        val repository = newRepository()
        runCurrent()

        repository.refresh()
        repository.awaitState { it is ProfileState.Available && it.isStale }
        signOut(repository)
        runCurrent()

        assertLogsHoldNoPersonalData()
    }
}
