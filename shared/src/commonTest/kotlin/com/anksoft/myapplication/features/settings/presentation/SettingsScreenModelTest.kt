package com.anksoft.myapplication.features.settings.presentation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.anksoft.myapplication.core.config.AppConfig
import com.anksoft.myapplication.core.config.AppEnvironment
import com.anksoft.myapplication.features.auth.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class SettingsScreenModelTest {

    private lateinit var repository: FakeAuthRepository

    @BeforeTest
    fun setUp() {
        // screenModelScope is Main-dispatched, so Main must be replaced.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeAuthRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createModel(environment: AppEnvironment = AppEnvironment.PROD) = SettingsScreenModel(
        authRepository = repository,
        appConfig = AppConfig.create(
            environment = environment,
            backendUrl = null,
            demoAllowed = environment == AppEnvironment.DEV,
            versionName = "2.1",
            versionCode = 42
        )
    )

    // AC-7
    @Test
    fun initialStateShowsEnvironmentAndVersionFromConfig() = runTest {
        val state = createModel(AppEnvironment.STAGE).state.value

        assertThat(state.environmentName).isEqualTo("STAGE")
        assertThat(state.versionName).isEqualTo("2.1")
        assertThat(state.versionCode).isEqualTo(42)
    }

    // AC-7
    @Test
    fun prodAlsoShowsEnvironmentName() = runTest {
        assertThat(createModel(AppEnvironment.PROD).state.value.environmentName).isEqualTo("PROD")
    }

    // AC-7
    @Test
    fun logoutEventLogsOutAndMarksStateLoggedOut() = runTest {
        val model = createModel()
        assertThat(model.state.value.isLoggedOut).isFalse()

        model.onEvent(SettingsEvent.Logout)

        assertThat(model.state.value.isLoggedOut).isTrue()
        assertThat(model.state.value.isLoading).isFalse()
        assertThat(repository.logoutCallCount).isEqualTo(1)
    }
}
