package com.anksoft.myapplication.core.di

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.anksoft.myapplication.core.config.AppConfig
import com.anksoft.myapplication.core.config.AppEnvironment
import com.anksoft.myapplication.core.consent.ConsentManager
import com.anksoft.myapplication.core.consent.FakeConsentManager
import com.anksoft.myapplication.core.consent.OptionalDataConsent
import com.anksoft.myapplication.core.config.TestAppConfigs
import com.anksoft.myapplication.core.crash.CrashGate
import com.anksoft.myapplication.core.crash.CrashReporter
import com.anksoft.myapplication.core.crash.FakeCrashReporter
import com.anksoft.myapplication.core.crash.PLATFORM_CRASH_REPORTER
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.logging.CONSOLE_LOG_WRITER
import com.anksoft.myapplication.core.logging.LogSeverity
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.LogWriter
import com.anksoft.myapplication.core.logging.error
import com.anksoft.myapplication.core.logging.RecordingLogWriter
import com.anksoft.myapplication.core.network.mock.MockAuthServer
import com.anksoft.myapplication.features.auth.domain.repository.AuthRepository
import com.anksoft.myapplication.features.consent.FakeConsentRepository
import com.anksoft.myapplication.features.consent.domain.AccountConsentManager
import com.anksoft.myapplication.features.consent.domain.repository.ConsentRepository
import com.anksoft.myapplication.core.preferences.AppLanguage
import com.anksoft.myapplication.core.preferences.AppPreferences
import com.anksoft.myapplication.core.session.FakeSessionObserver
import com.anksoft.myapplication.core.session.SessionExpiry
import com.anksoft.myapplication.core.session.SessionObserver
import com.anksoft.myapplication.core.session.SignOutReason
import com.anksoft.myapplication.core.preferences.LANGUAGE_KEY
import com.anksoft.myapplication.core.storage.SessionManager
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.test.runTest
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test

class InitKoinTest {

    // Host tests have no Logcat, so the real console writer must never be resolved.
    private val recorder = RecordingLogWriter()
    private val consoleOverride = module { single<LogWriter>(named(CONSOLE_LOG_WRITER)) { recorder } }

    // Host tests have no Android context, so the session storage is replaced as well: the consent
    // manager is created at start and reads the session.
    private val sessionStorage = MapSettings()
    private val sessionOverride = module { single<Settings> { sessionStorage } }

    // Host tests have no Android context, so the platform's preferences storage is replaced too.
    private val preferencesStorage = MapSettings()
    private val preferencesOverride = module { single<Settings>(AppPreferencesSettings) { preferencesStorage } }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun demoConfigWiresMockBackendEndToEnd() = runTest {
        val koin = initKoin(
            config = TestAppConfigs.demo(),
            platformModules = listOf(consoleOverride, sessionOverride)
        ).koin

        val demoUser = MockAuthServer.DEMO_USER
        val result = koin.get<AuthRepository>().login(demoUser.email, demoUser.password)

        assertThat(result).isInstanceOf(Result.Success::class)
    }

    // AC-1
    @Test
    fun koinProvidesTheGivenConfig() {
        val config = TestAppConfigs.remote("https://configured.test/api/")

        val koin = initKoin(config = config, platformModules = listOf(consoleOverride, sessionOverride)).koin

        assertThat(koin.get<AppConfig>()).isEqualTo(config)
    }

    // AC-1
    @Test
    fun platformModulesAreLoaded() {
        val platformModule = module { single<String>(named("platform")) { "from-platform" } }

        val koin = initKoin(
            config = TestAppConfigs.demo(),
            platformModules = listOf(consoleOverride, sessionOverride, platformModule)
        ).koin

        assertThat(koin.get<String>(named("platform"))).isEqualTo("from-platform")
    }

    // AC-1
    @Test
    fun startupLogsOneAppEntryWithEnvironmentAndVersion() {
        initKoin(config = TestAppConfigs.demo(), platformModules = listOf(consoleOverride, sessionOverride))

        val entry = recorder.entries.single { it.tag == LogTags.APP }
        assertThat(entry.severity).isEqualTo(LogSeverity.INFO)
        assertThat(entry.message).contains("env=DEV")
        assertThat(entry.message).contains("version=1.0")
    }

    // AC-1
    @Test
    fun loggerIsProvidedFromOnePlaceAndSharedAcrossConsumers() {
        val koin = initKoin(
            config = TestAppConfigs.demo(),
            platformModules = listOf(consoleOverride, sessionOverride)
        ).koin

        assertThat(koin.get<AppLogger>() === koin.get<AppLogger>()).isTrue()
    }

    // AC-9
    @Test
    fun preferencesUseTheirOwnStorageNotTheSessionStorage() {
        val koin = initKoin(
            config = TestAppConfigs.demo(),
            platformModules = listOf(consoleOverride, preferencesOverride, sessionOverride)
        ).koin

        koin.get<AppPreferences>().setLanguage(AppLanguage.TURKISH)
        // Signed out, so the consent manager has nothing of its own to write.
        koin.get<SessionManager>().saveToken("access-123")

        assertThat(preferencesStorage.keys).isEqualTo(setOf(LANGUAGE_KEY))
        assertThat(sessionStorage.keys).isEqualTo(setOf(SessionManager.KEY_TOKEN))
    }

    // AC-7
    @Test
    fun preferencesAreOneSharedInstance() {
        val koin = initKoin(
            config = TestAppConfigs.demo(),
            platformModules = listOf(consoleOverride, preferencesOverride, sessionOverride)
        ).koin

        assertThat(koin.get<AppPreferences>() === koin.get<AppPreferences>()).isTrue()
    }

    // AC-28
    @Test
    fun sessionObserversRegisteredInKoinHearAboutSignInAndExpiry() = runTest {
        val observer = FakeSessionObserver()
        val koin = initKoin(
            config = TestAppConfigs.demo(),
            platformModules = listOf(
                consoleOverride,
                sessionOverride,
                module { single<SessionObserver> { observer } }
            )
        ).koin

        val demoUser = MockAuthServer.DEMO_USER
        koin.get<AuthRepository>().login(demoUser.email, demoUser.password)
        koin.get<SessionExpiry>().expire()

        assertThat(observer.events).isEqualTo(
            listOf(
                FakeSessionObserver.Event.SignedIn,
                FakeSessionObserver.Event.SignedOut(SignOutReason.EXPIRED)
            )
        )
    }

    // AC-28
    @Test
    fun sessionExpiryIsOneSharedInstance() {
        val koin = initKoin(
            config = TestAppConfigs.demo(),
            platformModules = listOf(consoleOverride, sessionOverride)
        ).koin

        assertThat(koin.get<SessionExpiry>() === koin.get<SessionExpiry>()).isTrue()
    }

    // AC-26
    @Test
    fun theConsentFacadeAndTheSessionObserverAreTheSameAccountConsentManager() {
        val koin = initKoin(
            config = TestAppConfigs.demo(),
            platformModules = listOf(consoleOverride, sessionOverride)
        ).koin

        val manager = koin.get<AccountConsentManager>()

        assertThat(koin.get<ConsentManager>() === manager).isTrue()
        assertThat(koin.getAll<SessionObserver>().filterIsInstance<AccountConsentManager>().single() === manager)
            .isTrue()
    }

    // E5 rule 4: the first value must be ready before anyone asks.
    @Test
    fun theConsentManagerIsCreatedAtStartWithoutAnyoneAskingForIt() {
        var created = false
        val spyRepository = object : ConsentRepository by FakeConsentRepository() {
            override fun isSignedIn(): Boolean {
                created = true
                return false
            }
        }

        initKoin(
            config = TestAppConfigs.demo(),
            platformModules = listOf(
                consoleOverride,
                sessionOverride,
                module { single<ConsentRepository> { spyRepository } }
            )
        )

        assertThat(created).isTrue()
    }

    // AC-19
    @Test
    fun withoutASessionTheConsentFacadeSaysUnknown() {
        val koin = initKoin(
            config = TestAppConfigs.demo(),
            platformModules = listOf(consoleOverride, sessionOverride)
        ).koin

        assertThat(koin.get<ConsentManager>().optionalDataConsent.value).isEqualTo(OptionalDataConsent.UNKNOWN)
    }

    // AC-6, AC-27: a restart with a session starts from the cached decision.
    @Test
    fun aStoredSessionWithACachedGrantStartsTheFacadeAtGranted() {
        SessionManager(sessionStorage).apply {
            saveToken("access-123")
            saveUserId("demo-1")
            saveConsent("granted", 1)
        }

        val koin = initKoin(
            config = TestAppConfigs.demo(),
            platformModules = listOf(consoleOverride, sessionOverride)
        ).koin

        assertThat(koin.get<ConsentManager>().optionalDataConsent.value).isEqualTo(OptionalDataConsent.GRANTED)
    }

    // AC-2, AC-19: no vendor is bound in a host test, so crash reporting is a no-op.
    @Test
    fun appStartsWithNoCrashVendorAndErrorsAreSafeToLog() {
        val koin = initKoin(
            config = TestAppConfigs.demo(),
            platformModules = listOf(consoleOverride, sessionOverride)
        ).koin

        koin.get<AppLogger>().error(LogTags.APP, RuntimeException("boom")) { "caught" }

        assertThat(koin.get<CrashGate>().isOpen).isFalse()
    }

    // AC-5, AC-9: a bound vendor is switched on from the consent state read at start, and the
    // startup log line already reaches it as a breadcrumb.
    @Test
    fun aBoundVendorWithAGrantedConsentIsEnabledAtStartBeforeTheStartupLine() {
        val vendor = FakeCrashReporter()
        val vendorModule = module {
            single<Settings>(CrashReportingSettings) { MapSettings() }
            single<ConsentManager> { FakeConsentManager(OptionalDataConsent.GRANTED) }
            single<CrashReporter>(named(PLATFORM_CRASH_REPORTER)) { vendor }
        }

        val koin = initKoin(
            config = AppConfig.create(AppEnvironment.STAGE, null, false, "1.0", 1),
            platformModules = listOf(consoleOverride, sessionOverride, vendorModule)
        ).koin

        assertThat(koin.get<CrashGate>().isOpen).isTrue()
        assertThat(vendor.calls.contains(FakeCrashReporter.Call.SetCollection(true))).isTrue()
        assertThat(vendor.breadcrumbs.last()).contains("Started env=STAGE")
    }
}
