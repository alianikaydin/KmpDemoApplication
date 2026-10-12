package com.anksoft.myapplication.core.di

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.anksoft.myapplication.core.config.TestAppConfigs
import com.anksoft.myapplication.core.logging.CONSOLE_LOG_WRITER
import com.anksoft.myapplication.core.logging.LogWriter
import com.anksoft.myapplication.core.logging.RecordingLogWriter
import com.anksoft.myapplication.core.network.AuthTokenCache
import com.anksoft.myapplication.core.network.mock.MockAuthServer
import com.anksoft.myapplication.core.session.SessionObserver
import com.anksoft.myapplication.core.storage.SessionManager
import com.anksoft.myapplication.features.auth.domain.repository.AuthRepository
import com.anksoft.myapplication.features.profile.FakeProfileLocalDataSource
import com.anksoft.myapplication.features.profile.data.datasource.ProfileLocalDataSource
import com.anksoft.myapplication.features.profile.data.repository.ProfileRepositoryImpl
import com.anksoft.myapplication.features.profile.domain.model.ProfileState
import com.anksoft.myapplication.features.profile.domain.repository.ProfileRepository
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.Koin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the real Koin graph, the real HTTP client and the demo backend's mock engine, with a fake
 * profile store (host tests have no Android context for the database file). It proves the wiring
 * that unit tests with hand-built objects skip: sign-in saves the profile, `auth/me` answers it,
 * and a rejected refresh reaches the profile repository through `SessionExpiry`.
 */
class ProfileWiringTest {

    // Host tests have no Logcat or Android context: console writer and storages are replaced.
    private val recorder = RecordingLogWriter()
    private val consoleOverride = module { single<LogWriter>(named(CONSOLE_LOG_WRITER)) { recorder } }
    private val sessionOverride = module { single<Settings> { MapSettings() } }
    private val preferencesOverride = module { single<Settings>(AppPreferencesSettings) { MapSettings() } }

    private val demoUser = MockAuthServer.DEMO_USER
    private val consentUser = MockAuthServer.CONSENT_PENDING_USER

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    private fun startApp(): Koin = initKoin(
        config = TestAppConfigs.demo(),
        platformModules = listOf(consoleOverride, preferencesOverride, sessionOverride, fakeProfileStoreModule)
    ).koin

    private fun Koin.store(): FakeProfileLocalDataSource = get<ProfileLocalDataSource>() as FakeProfileLocalDataSource

    /** The repository works on other threads, so these wait in real time, not virtual time. */
    private suspend fun ProfileRepository.awaitState(matches: (ProfileState) -> Boolean): ProfileState =
        withContext(Dispatchers.Default) { withTimeout(10.seconds) { state.first(matches) } }

    private suspend fun awaitUntil(condition: () -> Boolean) {
        withContext(Dispatchers.Default) {
            withTimeout(10.seconds) {
                while (!condition()) delay(10)
            }
        }
    }

    // AC-2, AC-21
    @Test
    fun signingInShowsTheDemoUsersProfileAtOnceAndStoresIt() = runTest {
        val koin = startApp()
        val profile = koin.get<ProfileRepository>()

        koin.get<AuthRepository>().login(demoUser.email, demoUser.password)

        // No waiting: the profile comes with the sign-in response.
        val state = profile.state.value
        assertThat(state is ProfileState.Available).isTrue()
        val shown = (state as ProfileState.Available).profile
        assertThat(shown.email).isEqualTo("demo@example.com")
        assertThat(shown.name).isEqualTo("Demo User")
        assertThat(koin.store().rows).isEqualTo(mapOf(shown.userId to shown))
    }

    // AC-21
    @Test
    fun refreshingAsksTheDemoBackendForTheProfileAndStoresTheAnswer() = runTest {
        val koin = startApp()
        val profile = koin.get<ProfileRepository>()
        koin.get<AuthRepository>().login(demoUser.email, demoUser.password)
        val store = koin.store()
        assertThat(store.replaceCalls.size).isEqualTo(1)

        profile.refresh()

        awaitUntil { store.replaceCalls.size == 2 }
        val state = profile.awaitState { it is ProfileState.Available }
        assertThat((state as ProfileState.Available).isStale).isFalse()
        assertThat(state.profile.email).isEqualTo("demo@example.com")
    }

    // AC-16
    @Test
    fun theProfileLoadsWhileTheConsentDecisionIsStillOpen() = runTest {
        val koin = startApp()
        val profile = koin.get<ProfileRepository>()

        koin.get<AuthRepository>().login(consentUser.email, consentUser.password)

        val state = profile.state.value
        assertThat(state is ProfileState.Available).isTrue()
        assertThat((state as ProfileState.Available).profile.email).isEqualTo("consent@example.com")
    }

    // AC-9, AC-11
    @Test
    fun aRejectedRefreshEndsTheProfileAndEmptiesTheStoreThroughTheRealWiring() = runTest {
        val koin = startApp()
        val profile = koin.get<ProfileRepository>()
        val session = koin.get<SessionManager>()
        koin.get<AuthRepository>().login(demoUser.email, demoUser.password)
        // The access token is no longer accepted and the refresh token cannot be exchanged.
        session.saveToken("expired-access")
        session.saveRefreshToken("revoked-refresh")
        koin.get<AuthTokenCache>().clear()

        profile.refresh()

        profile.awaitState { it == ProfileState.SignedOut }
        awaitUntil { koin.store().rows.isEmpty() }
        assertThat(session.getToken()).isNull()
    }

    // AC-10
    @Test
    fun loggingOutShowsSignedOutAtOnceAndEmptiesTheStore() = runTest {
        val koin = startApp()
        val profile = koin.get<ProfileRepository>()
        val authRepository = koin.get<AuthRepository>()
        authRepository.login(demoUser.email, demoUser.password)

        authRepository.logout()

        // No waiting: the profile is gone when logout returns.
        assertThat(profile.state.value).isEqualTo(ProfileState.SignedOut)
        awaitUntil { koin.store().rows.isEmpty() }
    }

    // AC-12
    @Test
    fun signingInAsAnotherAccountNeverShowsTheFirstAccountsProfile() = runTest {
        val koin = startApp()
        val profile = koin.get<ProfileRepository>()
        val authRepository = koin.get<AuthRepository>()
        authRepository.login(demoUser.email, demoUser.password)
        authRepository.logout()

        authRepository.login(consentUser.email, consentUser.password)

        val shown = (profile.state.value as ProfileState.Available).profile
        assertThat(shown.email).isEqualTo("consent@example.com")
        awaitUntil { koin.store().rows.keys == setOf(shown.userId) }
    }

    @Test
    fun theProfileRepositoryAndTheSessionObserverAreTheSameInstance() {
        val koin = startApp()

        val repository = koin.get<ProfileRepository>()

        assertThat(repository is ProfileRepositoryImpl).isTrue()
        assertThat(koin.get<ProfileRepositoryImpl>() === repository).isTrue()
        assertThat(koin.getAll<SessionObserver>().any { it === repository }).isTrue()
    }

    @Test
    fun withoutASessionTheProfileIsSignedOutAtStart() {
        val koin = startApp()

        assertThat(koin.get<ProfileRepository>().state.value).isEqualTo(ProfileState.SignedOut)
    }
}
