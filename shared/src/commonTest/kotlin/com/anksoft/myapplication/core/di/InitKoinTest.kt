package com.anksoft.myapplication.core.di

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.anksoft.myapplication.core.config.AppConfig
import com.anksoft.myapplication.core.config.TestAppConfigs
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.logging.CONSOLE_LOG_WRITER
import com.anksoft.myapplication.core.logging.LogSeverity
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.LogWriter
import com.anksoft.myapplication.core.logging.RecordingLogWriter
import com.anksoft.myapplication.core.network.mock.MockAuthServer
import com.anksoft.myapplication.features.auth.domain.repository.AuthRepository
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.test.runTest
import org.koin.core.context.loadKoinModules
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test

class InitKoinTest {

    // Host tests have no Logcat, so the real console writer must never be resolved.
    private val recorder = RecordingLogWriter()
    private val consoleOverride = module { single<LogWriter>(named(CONSOLE_LOG_WRITER)) { recorder } }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun demoConfigWiresMockBackendEndToEnd() = runTest {
        val koin = initKoin(config = TestAppConfigs.demo(), platformModules = listOf(consoleOverride)).koin
        // Platform settings need an Android context; swap in memory-backed settings.
        loadKoinModules(module { single<Settings> { MapSettings() } })

        val demoUser = MockAuthServer.DEMO_USER
        val result = koin.get<AuthRepository>().login(demoUser.email, demoUser.password)

        assertThat(result).isInstanceOf(Result.Success::class)
    }

    // AC-1
    @Test
    fun koinProvidesTheGivenConfig() {
        val config = TestAppConfigs.remote("https://configured.test/api/")

        val koin = initKoin(config = config, platformModules = listOf(consoleOverride)).koin

        assertThat(koin.get<AppConfig>()).isEqualTo(config)
    }

    // AC-1
    @Test
    fun platformModulesAreLoaded() {
        val platformModule = module { single<String>(named("platform")) { "from-platform" } }

        val koin = initKoin(config = TestAppConfigs.demo(), platformModules = listOf(consoleOverride, platformModule)).koin

        assertThat(koin.get<String>(named("platform"))).isEqualTo("from-platform")
    }

    // AC-1
    @Test
    fun startupLogsOneAppEntryWithEnvironmentAndVersion() {
        initKoin(config = TestAppConfigs.demo(), platformModules = listOf(consoleOverride))

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
            platformModules = listOf(consoleOverride)
        ).koin

        assertThat(koin.get<AppLogger>() === koin.get<AppLogger>()).isTrue()
    }
}
