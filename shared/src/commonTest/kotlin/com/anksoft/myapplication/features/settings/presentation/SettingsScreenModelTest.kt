package com.anksoft.myapplication.features.settings.presentation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import cafe.adriel.voyager.core.model.screenModelScope
import com.anksoft.myapplication.core.config.AppConfig
import com.anksoft.myapplication.core.config.AppEnvironment
import com.anksoft.myapplication.core.consent.OptionalDataConsent
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.logging.NoOpLogger
import com.anksoft.myapplication.core.preferences.AppLanguage
import com.anksoft.myapplication.core.preferences.ContentLanguage
import com.anksoft.myapplication.core.preferences.FakeAppPreferences
import com.anksoft.myapplication.core.presentation.UiText
import com.anksoft.myapplication.features.auth.FakeAuthRepository
import com.anksoft.myapplication.features.consent.FakeConsentRepository
import com.anksoft.myapplication.features.consent.domain.AccountConsentManager
import com.anksoft.myapplication.features.consent.domain.model.AccountConsent
import com.anksoft.myapplication.features.consent.domain.model.ConsentChoice
import com.anksoft.myapplication.features.consent.domain.model.ConsentDecision
import com.anksoft.myapplication.features.consent.domain.model.ConsentStatus
import com.anksoft.myapplication.features.consent.domain.usecase.ObserveConsentTextsUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import myapplication.shared.generated.resources.Res
import myapplication.shared.generated.resources.consent_error_text_updated
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class SettingsScreenModelTest {

    private lateinit var repository: FakeAuthRepository
    private lateinit var preferences: FakeAppPreferences
    private lateinit var consent: FakeConsentRepository

    @BeforeTest
    fun setUp() {
        // screenModelScope is Main-dispatched, so Main must be replaced.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeAuthRepository()
        preferences = FakeAppPreferences()
        consent = FakeConsentRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createManager() = AccountConsentManager(
        repository = consent,
        scope = CoroutineScope(UnconfinedTestDispatcher()),
        logger = NoOpLogger
    )

    private fun createModel(
        environment: AppEnvironment = AppEnvironment.PROD,
        manager: AccountConsentManager = createManager()
    ) = SettingsScreenModel(
        authRepository = repository,
        appConfig = AppConfig.create(
            environment = environment,
            backendUrl = null,
            demoAllowed = environment == AppEnvironment.DEV,
            versionName = "2.1",
            versionCode = 42
        ),
        appPreferences = preferences,
        consentManager = manager,
        observeConsentTexts = ObserveConsentTextsUseCase(consent, ContentLanguage(preferences) { "en" })
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

    // region privacy section

    private val granted = AccountConsent(ConsentDecision.GRANTED, textVersion = 1)
    private val denied = AccountConsent(ConsentDecision.DENIED, textVersion = 1)
    private fun choice(granted: Boolean) = ConsentChoice(granted = granted, textVersion = 1, textLanguage = "en")

    // AC-12
    @Test
    fun openingSettingsLooksAtTheAccountDecisionAgain() = runTest {
        val manager = createManager()
        val before = consent.fetchCount

        createModel(manager = manager)

        assertThat(consent.fetchCount).isEqualTo(before + 1)
    }

    // AC-14
    @Test
    fun aGrantedDecisionShowsTheSwitchOnWithTheBackendLabelAndLink() = runTest {
        consent.fetchResult = Result.Success(granted)

        val privacy = createModel().state.value.privacy

        assertThat(privacy.isChecked).isTrue()
        assertThat(privacy.isToggleEnabled).isTrue()
        assertThat(privacy.label).isEqualTo(FakeConsentRepository.texts().label)
        assertThat(privacy.policyUrl).isEqualTo("https://example.com/privacy")
        assertThat(privacy.showRetry).isFalse()
    }

    // AC-14
    @Test
    fun noDecisionAndADeniedDecisionShowTheSwitchOff() = runTest {
        val undecided = createModel().state.value.privacy
        consent.fetchResult = Result.Success(denied)
        val deniedPrivacy = createModel().state.value.privacy

        assertThat(undecided.isChecked).isFalse()
        assertThat(undecided.isToggleEnabled).isTrue()
        assertThat(deniedPrivacy.isChecked).isFalse()
        assertThat(deniedPrivacy.isToggleEnabled).isTrue()
    }

    // AC-13
    @Test
    fun anUnavailableDecisionShowsTheSwitchOffAndDisabledWithRetry() = runTest {
        consent.fetchResult = Result.Failure(DataError.Remote.NO_INTERNET)

        val privacy = createModel().state.value.privacy

        assertThat(privacy.isChecked).isFalse()
        assertThat(privacy.isToggleEnabled).isFalse()
        assertThat(privacy.showRetry).isTrue()
    }

    // AC-13
    @Test
    fun aFailedTextLoadDisablesTheSwitchUntilRetrySucceeds() = runTest {
        consent.textsProvider = { Result.Failure(DataError.Remote.NO_INTERNET) }
        val model = createModel()

        assertThat(model.state.value.privacy.isToggleEnabled).isFalse()
        assertThat(model.state.value.privacy.showRetry).isTrue()

        consent.textsProvider = { tag -> Result.Success(FakeConsentRepository.texts(language = tag)) }
        model.onEvent(SettingsEvent.RetryConsent)

        assertThat(model.state.value.privacy.isToggleEnabled).isTrue()
        assertThat(model.state.value.privacy.showRetry).isFalse()
    }

    // AC-13
    @Test
    fun retryAfterAnUnavailableDecisionFetchesItAgain() = runTest {
        consent.fetchResult = Result.Failure(DataError.Remote.NO_INTERNET)
        val model = createModel()
        consent.fetchResult = Result.Success(granted)

        model.onEvent(SettingsEvent.RetryConsent)

        assertThat(model.state.value.privacy.isChecked).isTrue()
        assertThat(model.state.value.privacy.showRetry).isFalse()
    }

    // AC-15
    @Test
    fun switchingOffRecordsADenialForTheShownTextAndTurnsCollectionOff() = runTest {
        consent.fetchResult = Result.Success(granted)
        val manager = createManager()
        val model = createModel(manager = manager)

        model.onEvent(SettingsEvent.ConsentToggle(false))

        assertThat(consent.savedChoices).isEqualTo(listOf(choice(false)))
        assertThat(model.state.value.privacy.isChecked).isFalse()
        assertThat(model.state.value.privacy.error).isNull()
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.DENIED)
    }

    // AC-8
    @Test
    fun switchingOnRecordsAGrantForTheShownText() = runTest {
        val model = createModel()

        model.onEvent(SettingsEvent.ConsentToggle(true))

        assertThat(consent.savedChoices).isEqualTo(listOf(choice(true)))
        assertThat(model.state.value.privacy.isChecked).isTrue()
    }

    // AC-16
    @Test
    fun aFailedSwitchOffKeepsTheSwitchOnShowsAnErrorAndCollectionStaysOff() = runTest {
        consent.fetchResult = Result.Success(granted)
        consent.saveResult = Result.Failure(DataError.Remote.NO_INTERNET)
        val manager = createManager()
        val model = createModel(manager = manager)

        model.onEvent(SettingsEvent.ConsentToggle(false))

        val privacy = model.state.value.privacy
        assertThat(privacy.isChecked).isTrue()
        assertThat(privacy.error).isNotNull()
        assertThat(privacy.isSaving).isFalse()
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.DENIED)
    }

    // AC-16
    @Test
    fun aFailedSwitchOnKeepsTheSwitchOffAndShowsAnError() = runTest {
        consent.fetchResult = Result.Success(denied)
        consent.saveResult = Result.Failure(DataError.Remote.NO_INTERNET)
        val manager = createManager()
        val model = createModel(manager = manager)

        model.onEvent(SettingsEvent.ConsentToggle(true))

        val privacy = model.state.value.privacy
        assertThat(privacy.isChecked).isFalse()
        assertThat(privacy.error).isNotNull()
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.DENIED)
    }

    // AC-16
    @Test
    fun theErrorClearsOnTheNextAttempt() = runTest {
        consent.saveResult = Result.Failure(DataError.Remote.NO_INTERNET)
        val model = createModel()
        model.onEvent(SettingsEvent.ConsentToggle(true))
        assertThat(model.state.value.privacy.error).isNotNull()
        consent.saveResult = null

        model.onEvent(SettingsEvent.ConsentToggle(true))

        assertThat(model.state.value.privacy.error).isNull()
        assertThat(model.state.value.privacy.isChecked).isTrue()
    }

    // AC-15, AC-16: leaving Settings (the ScreenModel is disposed) must not drop the write.
    @Test
    fun aSwitchOffIsStillSavedWhenSettingsIsLeftBeforeTheBackendAnswers() = runTest {
        consent.fetchResult = Result.Success(granted)
        val gate = CompletableDeferred<Unit>()
        consent.saveGate = gate
        val manager = createManager()
        val model = createModel(manager = manager)
        model.onEvent(SettingsEvent.ConsentToggle(false))

        // What Voyager does when the screen leaves the back stack.
        model.screenModelScope.cancel()
        gate.complete(Unit)

        assertThat(consent.savedChoices).isEqualTo(listOf(choice(false)))
        assertThat(manager.status.value).isEqualTo(ConsentStatus.Denied)
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.DENIED)
    }

    // AC-15
    @Test
    fun theSwitchIsDisabledWhileASaveIsRunningAndShowsTheRequestedValue() = runTest {
        val gate = CompletableDeferred<Unit>()
        consent.saveGate = gate
        val model = createModel()

        model.onEvent(SettingsEvent.ConsentToggle(true))

        val saving = model.state.value.privacy
        assertThat(saving.isSaving).isTrue()
        assertThat(saving.isToggleEnabled).isFalse()
        assertThat(saving.isChecked).isTrue()

        // A second change while saving is ignored: no out-of-order answers.
        model.onEvent(SettingsEvent.ConsentToggle(false))
        assertThat(consent.savedChoices).isEqualTo(listOf(choice(true)))

        gate.complete(Unit)

        val done = model.state.value.privacy
        assertThat(done.isSaving).isFalse()
        assertThat(done.isToggleEnabled).isTrue()
        assertThat(done.isChecked).isTrue()
    }

    // AC-22
    @Test
    fun aRefusedTextVersionShowsTheUpdateNoticeAndReloadsTheText() = runTest {
        consent.saveResult = Result.Failure(DataError.Remote.UNPROCESSABLE)
        val model = createModel()
        val requestsBefore = consent.textRequests.size

        model.onEvent(SettingsEvent.ConsentToggle(true))

        assertThat(model.state.value.privacy.error)
            .isEqualTo(UiText.Resource(Res.string.consent_error_text_updated))
        assertThat(consent.textRequests.size).isEqualTo(requestsBefore + 1)
    }

    // AC-14
    @Test
    fun theSwitchIsDisabledWhileNoSessionExists() = runTest {
        consent.signedIn = false

        val privacy = createModel().state.value.privacy

        assertThat(privacy.isToggleEnabled).isFalse()
        assertThat(privacy.isChecked).isFalse()
    }

    // endregion
}
