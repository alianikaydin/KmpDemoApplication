package com.anksoft.myapplication.core.di

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.startsWith
import com.anksoft.myapplication.core.config.TestAppConfigs
import com.anksoft.myapplication.core.consent.ConsentManager
import com.anksoft.myapplication.core.consent.OptionalDataConsent
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.logging.CONSOLE_LOG_WRITER
import com.anksoft.myapplication.core.logging.LogWriter
import com.anksoft.myapplication.core.logging.RecordingLogWriter
import com.anksoft.myapplication.core.network.AuthTokenCache
import com.anksoft.myapplication.core.network.mock.MockAuthServer
import com.anksoft.myapplication.core.session.SessionExpiry
import com.anksoft.myapplication.core.storage.SessionManager
import com.anksoft.myapplication.features.auth.domain.repository.AuthRepository
import com.anksoft.myapplication.features.consent.domain.AccountConsentManager
import com.anksoft.myapplication.features.consent.domain.model.AccountConsent
import com.anksoft.myapplication.features.consent.domain.model.ConsentChoice
import com.anksoft.myapplication.features.consent.domain.model.ConsentDecision
import com.anksoft.myapplication.features.consent.domain.model.ConsentStatus
import com.anksoft.myapplication.features.consent.domain.repository.ConsentRepository
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.Dispatchers
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
 * Runs the real Koin graph, the real HTTP client and the demo backend's mock engine, whose consent
 * endpoints are protected (401 without a valid Bearer token). It proves the wiring between the
 * network layer, the session and the consent manager that unit tests with hand-built objects skip:
 * `onSessionExpired` reaches `SessionExpiry`, which reaches `AccountConsentManager`.
 */
class KoinSessionWiringTest {

    // Host tests have no Logcat or Android context: console writer and session storage are replaced.
    private val recorder = RecordingLogWriter()
    private val consoleOverride = module { single<LogWriter>(named(CONSOLE_LOG_WRITER)) { recorder } }
    private val sessionStorage = MapSettings()
    private val sessionOverride = module { single<Settings> { sessionStorage } }

    private val demoUser = MockAuthServer.DEMO_USER
    private val consentUser = MockAuthServer.CONSENT_PENDING_USER

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    private fun startApp(): Koin = initKoin(
        config = TestAppConfigs.demo(),
        platformModules = listOf(consoleOverride, sessionOverride)
    ).koin

    /** The consent manager fetches on another thread, so this waits in real time, not virtual time. */
    private suspend fun AccountConsentManager.awaitStatus(matches: (ConsentStatus) -> Boolean): ConsentStatus =
        withContext(Dispatchers.Default) { withTimeout(10.seconds) { status.first(matches) } }

    // N7, AC-28
    @Test
    fun aRejectedRefreshOnAProtectedCallEndsTheSessionThroughTheRealWiring() = runTest {
        val koin = startApp()
        val manager = koin.get<AccountConsentManager>()
        val session = koin.get<SessionManager>()
        koin.get<AuthRepository>().login(demoUser.email, demoUser.password)
        manager.awaitStatus { it is ConsentStatus.Granted }
        // The access token is no longer accepted and the refresh token cannot be exchanged.
        session.saveToken("expired-access")
        session.saveRefreshToken("revoked-refresh")
        koin.get<AuthTokenCache>().clear()

        val result = koin.get<ConsentRepository>().fetchAccountConsent()

        assertThat(result).isEqualTo(Result.Failure(DataError.Remote.UNAUTHORIZED))
        assertThat(session.getToken()).isNull()
        assertThat(session.getRefreshToken()).isNull()
        assertThat(session.getConsentStatus()).isNull()
        assertThat(koin.get<SessionExpiry>().consumeExpiredNotice()).isTrue()
        assertThat(manager.status.value).isEqualTo(ConsentStatus.SignedOut)
        assertThat(koin.get<ConsentManager>().optionalDataConsent.value).isEqualTo(OptionalDataConsent.UNKNOWN)
    }

    // N7, AC-28
    @Test
    fun anExpiredAccessTokenIsRefreshedAndTheProtectedCallRetriedThroughTheRealWiring() = runTest {
        val koin = startApp()
        val manager = koin.get<AccountConsentManager>()
        val session = koin.get<SessionManager>()
        koin.get<AuthRepository>().login(demoUser.email, demoUser.password)
        manager.awaitStatus { it is ConsentStatus.Granted }
        session.saveToken("expired-access")
        koin.get<AuthTokenCache>().clear()

        val result = koin.get<ConsentRepository>().fetchAccountConsent()

        assertThat(result).isEqualTo(Result.Success(AccountConsent(ConsentDecision.GRANTED, textVersion = 1)))
        assertThat(session.getToken()).isNotEqualTo("expired-access")
        assertThat(session.getToken().orEmpty()).startsWith("mock-access-")
        assertThat(koin.get<SessionExpiry>().consumeExpiredNotice()).isFalse()
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.GRANTED)
    }

    // AC-6, AC-18
    @Test
    fun signingInFetchesTheAccountsDecisionAndSigningOutSwitchesTheFacadeOff() = runTest {
        val koin = startApp()
        val manager = koin.get<AccountConsentManager>()

        koin.get<AuthRepository>().login(demoUser.email, demoUser.password)
        manager.awaitStatus { it is ConsentStatus.Granted }
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.GRANTED)

        koin.get<AuthRepository>().logout()

        // No waiting: the facade is off when logout returns.
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.UNKNOWN)
        assertThat(koin.get<SessionManager>().getConsentStatus()).isNull()
    }

    // AC-7, AC-10
    @Test
    fun anAccountWithoutADecisionIsOfferedThePromptOncePerSession() = runTest {
        val koin = startApp()
        val manager = koin.get<AccountConsentManager>()

        koin.get<AuthRepository>().login(consentUser.email, consentUser.password)
        manager.awaitStatus { it == ConsentStatus.Undecided }

        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.UNKNOWN)
        assertThat(manager.claimPrompt()).isTrue()
        assertThat(manager.claimPrompt()).isFalse()
    }

    // AC-8, AC-17, AC-18
    @Test
    fun aDecisionMadeInTheAppSurvivesSigningOutAndInBecauseTheBackendKeepsIt() = runTest {
        val koin = startApp()
        val manager = koin.get<AccountConsentManager>()
        val authRepository = koin.get<AuthRepository>()
        authRepository.login(consentUser.email, consentUser.password)
        manager.awaitStatus { it == ConsentStatus.Undecided }

        val result = manager.decide(ConsentChoice(granted = true, textVersion = 1, textLanguage = "en"))

        assertThat(result).isEqualTo(Result.Success(Unit))
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.GRANTED)
        assertThat(koin.get<SessionManager>().getConsentStatus()).isEqualTo("granted")

        authRepository.logout()
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.UNKNOWN)
        authRepository.login(consentUser.email, consentUser.password)
        manager.awaitStatus { it is ConsentStatus.Granted }

        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.GRANTED)
        assertThat(manager.claimPrompt()).isFalse()
    }
}
