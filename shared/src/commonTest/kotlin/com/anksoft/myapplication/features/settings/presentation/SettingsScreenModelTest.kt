package com.anksoft.myapplication.features.settings.presentation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.anksoft.myapplication.core.config.AppConfig
import com.anksoft.myapplication.core.config.AppEnvironment
import com.anksoft.myapplication.core.preferences.AppLanguage
import com.anksoft.myapplication.core.preferences.FakeAppPreferences
import com.anksoft.myapplication.core.preferences.ThemeMode
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
    private lateinit var preferences: FakeAppPreferences

    @BeforeTest
    fun setUp() {
        // screenModelScope is Main-dispatched, so Main must be replaced.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeAuthRepository()
        preferences = FakeAppPreferences()
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
        ),
        appPreferences = preferences
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

    // AC-5, AC-7
    @Test
    fun initialStateShowsTheLanguageStoredInPreferences() = runTest {
        preferences = FakeAppPreferences(AppLanguage.TURKISH)

        assertThat(createModel().state.value.selectedLanguage).isEqualTo(AppLanguage.TURKISH)
    }

    // AC-6
    @Test
    fun selectingALanguageWritesItToPreferencesAndUpdatesState() = runTest {
        val model = createModel()

        model.onEvent(SettingsEvent.LanguageSelect(AppLanguage.ENGLISH))

        assertThat(preferences.setLanguageCalls).isEqualTo(listOf(AppLanguage.ENGLISH))
        assertThat(model.state.value.selectedLanguage).isEqualTo(AppLanguage.ENGLISH)
    }

    // AC-5
    @Test
    fun selectingSystemDefaultIsStoredAsSystem() = runTest {
        preferences = FakeAppPreferences(AppLanguage.TURKISH)
        val model = createModel()

        model.onEvent(SettingsEvent.LanguageSelect(AppLanguage.SYSTEM))

        assertThat(model.state.value.selectedLanguage).isEqualTo(AppLanguage.SYSTEM)
    }

    // AC-6
    @Test
    fun languageChangedElsewhereIsReflectedInState() = runTest {
        val model = createModel()

        preferences.setLanguage(AppLanguage.TURKISH)

        assertThat(model.state.value.selectedLanguage).isEqualTo(AppLanguage.TURKISH)
    }

    // AC-7
    @Test
    fun changingLanguageKeepsEnvironmentAndVersionUntouched() = runTest {
        val model = createModel(AppEnvironment.STAGE)

        model.onEvent(SettingsEvent.LanguageSelect(AppLanguage.TURKISH))

        val state = model.state.value
        assertThat(state.environmentName).isEqualTo("STAGE")
        assertThat(state.versionName).isEqualTo("2.1")
        assertThat(state.versionCode).isEqualTo(42)
    }

    // AC-2
    @Test
    fun initialStateShowsTheStoredThemeMode() = runTest {
        preferences = FakeAppPreferences(initialThemeMode = ThemeMode.DARK)

        assertThat(createModel().state.value.selectedThemeMode).isEqualTo(ThemeMode.DARK)
    }

    // AC-5
    @Test
    fun themeModeSelectWritesThePreference() = runTest {
        val model = createModel()

        model.onEvent(SettingsEvent.ThemeModeSelect(ThemeMode.DARK))

        assertThat(preferences.setThemeModeCalls).isEqualTo(listOf(ThemeMode.DARK))
        assertThat(model.state.value.selectedThemeMode).isEqualTo(ThemeMode.DARK)
    }

    // AC-5
    @Test
    fun stateFollowsThemeChangesMadeElsewhere() = runTest {
        val model = createModel()

        preferences.setThemeMode(ThemeMode.LIGHT)

        assertThat(model.state.value.selectedThemeMode).isEqualTo(ThemeMode.LIGHT)
    }
}
