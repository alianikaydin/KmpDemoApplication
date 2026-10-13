package com.anksoft.myapplication.core.di

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.anksoft.kmpdemo.contract.app.AppPlatforms
import com.anksoft.kmpdemo.contract.app.VersionPolicyDto
import com.anksoft.myapplication.core.config.AppConfig
import com.anksoft.myapplication.core.config.TestAppConfigs
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.logging.CONSOLE_LOG_WRITER
import com.anksoft.myapplication.core.logging.LogWriter
import com.anksoft.myapplication.core.logging.NoOpLogger
import com.anksoft.myapplication.core.logging.RecordingLogWriter
import com.anksoft.myapplication.core.network.createHttpClient
import com.anksoft.myapplication.core.network.mock.DemoLaunchArguments
import com.anksoft.myapplication.core.network.mock.MockAuthServer
import com.anksoft.myapplication.core.network.mock.createMockEngine
import com.anksoft.myapplication.core.storage.SessionManager
import com.anksoft.myapplication.features.update.FakeAppStore
import com.anksoft.myapplication.features.update.data.datasource.VersionPolicyRemoteDataSource
import com.anksoft.myapplication.features.update.domain.AppStore
import com.anksoft.myapplication.features.update.domain.model.UpdatePlatform
import com.anksoft.myapplication.features.update.domain.model.UpdateRequirement
import com.anksoft.myapplication.features.update.domain.usecase.CheckForUpdateUseCase
import com.anksoft.myapplication.features.update.presentation.AppUpdateController
import com.anksoft.myapplication.features.update.presentation.AppUpdateState
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.koin.core.Koin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** The update feature through the real Koin graph and the mock backend (see InitKoinTest for the graph itself). */
class UpdateWiringTest {

    // Host tests have no Android context, so the platform-bound pieces are replaced as in InitKoinTest.
    private val consoleOverride = module { single<LogWriter>(named(CONSOLE_LOG_WRITER)) { RecordingLogWriter() } }
    private val sessionStorage = MapSettings()
    private val sessionOverride = module { single<Settings> { sessionStorage } }
    private val preferencesOverride = module { single<Settings>(AppPreferencesSettings) { MapSettings() } }
    private val appStoreOverride = module { single<AppStore> { FakeAppStore(UpdatePlatform.ANDROID) } }

    private fun start(config: AppConfig = TestAppConfigs.demo()): Koin = initKoin(
        config = config,
        platformModules = listOf<Module>(
            consoleOverride,
            sessionOverride,
            fakeProfileStoreModule,
            preferencesOverride,
            appStoreOverride,
        ),
    ).koin

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    // AC-19, AC-27
    @Test
    fun theDemoBuildNeedsNoUpdateByDefaultAndLeavesTheSessionKeysAlone() = runTest {
        val koin = start()
        val session = koin.get<SessionManager>().apply {
            saveToken("access-1")
            saveRefreshToken("refresh-1")
        }

        val requirement = koin.get<CheckForUpdateUseCase>()(UpdatePlatform.ANDROID)

        assertThat(requirement).isEqualTo(UpdateRequirement.None)
        assertThat(session.getToken()).isEqualTo("access-1")
        assertThat(session.getRefreshToken()).isEqualTo("refresh-1")
    }

    // AC-6, AC-19
    @Test
    fun theRequiredLaunchArgumentMakesTheControllerRequireAnUpdateWithoutTouchingTheSession() = runTest {
        val koin = start()
        val session = koin.get<SessionManager>().apply {
            saveToken("access-1")
            saveRefreshToken("refresh-1")
        }
        DemoLaunchArguments.apply(koin, "required")

        val controller = koin.get<AppUpdateController>()
        controller.start()
        val state = controller.state.first { it !is AppUpdateState.None }

        assertThat(state).isEqualTo(AppUpdateState.Required())
        assertThat(session.getToken()).isEqualTo("access-1")
        assertThat(session.getRefreshToken()).isEqualTo("refresh-1")
    }

    // AC-19
    @Test
    fun theOptionalLaunchArgumentOffersTheUpdate() = runTest {
        val koin = start()
        DemoLaunchArguments.apply(koin, "optional")

        val requirement = koin.get<CheckForUpdateUseCase>()(UpdatePlatform.ANDROID)

        assertThat(requirement is UpdateRequirement.Optional).isTrue()
    }

    // AC-22
    @Test
    fun theCheckRunsWithoutASessionAndWithConsentDenied() = runTest {
        val koin = start()
        koin.get<SessionManager>().saveConsent("denied", 1)
        DemoLaunchArguments.apply(koin, "required")

        val requirement = koin.get<CheckForUpdateUseCase>()(UpdatePlatform.ANDROID)

        assertThat(requirement).isEqualTo(UpdateRequirement.Required)
    }

    // AC-13
    @Test
    fun theControllerIsOneInstanceForTheWholeProcess() {
        val koin = start()

        assertThat(koin.get<AppUpdateController>() === koin.get<AppUpdateController>()).isTrue()
    }

    // AC-19
    @Test
    fun theLaunchArgumentDoesNothingInABuildWithARealBackend() = runTest {
        val koin = start(TestAppConfigs.remote("https://api.test/api/v1/"))
        DemoLaunchArguments.apply(koin, "required")

        // The mock exists in every graph, so ask it directly: it must still answer "no update".
        val client = createHttpClient(
            engine = createMockEngine(koin.get<MockAuthServer>()),
            config = TestAppConfigs.demo(),
            sessionManager = koin.get(),
            logger = NoOpLogger,
        )
        try {
            val answer = VersionPolicyRemoteDataSource(client, NoOpLogger).getVersionPolicy(AppPlatforms.ANDROID)

            assertThat(answer).isEqualTo(Result.Success(VersionPolicyDto("0.0.0", "0.0.0")))
        } finally {
            client.close()
        }
    }

    // AC-19
    @Test
    fun noLaunchArgumentLeavesTheDefaultPolicyInPlace() = runTest {
        val koin = start()
        DemoLaunchArguments.apply(koin, null)

        assertThat(koin.get<CheckForUpdateUseCase>()(UpdatePlatform.ANDROID)).isEqualTo(UpdateRequirement.None)
    }
}
