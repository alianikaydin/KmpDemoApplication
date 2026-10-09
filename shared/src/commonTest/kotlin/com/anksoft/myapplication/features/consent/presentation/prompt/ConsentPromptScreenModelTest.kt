package com.anksoft.myapplication.features.consent.presentation.prompt

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.anksoft.myapplication.core.consent.OptionalDataConsent
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.logging.NoOpLogger
import com.anksoft.myapplication.core.preferences.ContentLanguage
import com.anksoft.myapplication.core.preferences.FakeAppPreferences
import com.anksoft.myapplication.core.presentation.UiText
import com.anksoft.myapplication.features.consent.FakeConsentRepository
import com.anksoft.myapplication.features.consent.domain.AccountConsentManager
import com.anksoft.myapplication.features.consent.domain.model.ConsentChoice
import com.anksoft.myapplication.features.consent.domain.usecase.ConsentTextsLoad
import com.anksoft.myapplication.features.consent.domain.usecase.ObserveConsentTextsUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import myapplication.shared.generated.resources.Res
import myapplication.shared.generated.resources.consent_error_text_updated
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class ConsentPromptScreenModelTest {

    private lateinit var consent: FakeConsentRepository
    private lateinit var manager: AccountConsentManager

    @BeforeTest
    fun setUp() {
        // screenModelScope is Main-dispatched, so Main must be replaced.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        consent = FakeConsentRepository()
        manager = AccountConsentManager(
            repository = consent,
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            logger = NoOpLogger
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createModel() = ConsentPromptScreenModel(
        consentManager = manager,
        observeConsentTexts = ObserveConsentTextsUseCase(consent, ContentLanguage(FakeAppPreferences()) { "en" })
    )

    private fun choice(granted: Boolean) = ConsentChoice(granted = granted, textVersion = 1, textLanguage = "en")

    // AC-8
    @Test
    fun acceptingRecordsAGrantForTheShownTextAndClosesThePrompt() = runTest {
        val model = createModel()

        model.onEvent(ConsentPromptEvent.Accept)

        assertThat(consent.savedChoices).isEqualTo(listOf(choice(true)))
        assertThat(model.state.value.isDone).isTrue()
        assertThat(model.state.value.error).isNull()
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.GRANTED)
    }

    // AC-9
    @Test
    fun rejectingRecordsADenialAndClosesThePrompt() = runTest {
        val model = createModel()

        model.onEvent(ConsentPromptEvent.Reject)

        assertThat(consent.savedChoices).isEqualTo(listOf(choice(false)))
        assertThat(model.state.value.isDone).isTrue()
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.DENIED)
    }

    // AC-11
    @Test
    fun aFailedSaveKeepsThePromptOpenWithAnError() = runTest {
        consent.saveResult = Result.Failure(DataError.Remote.NO_INTERNET)
        val model = createModel()

        model.onEvent(ConsentPromptEvent.Accept)

        val state = model.state.value
        assertThat(state.isDone).isFalse()
        assertThat(state.isSaving).isFalse()
        assertThat(state.error).isNotNull()
        assertThat(manager.optionalDataConsent.value).isEqualTo(OptionalDataConsent.UNKNOWN)
    }

    // AC-11
    @Test
    fun theUserCanTryAgainAfterAFailedSave() = runTest {
        consent.saveResult = Result.Failure(DataError.Remote.NO_INTERNET)
        val model = createModel()
        model.onEvent(ConsentPromptEvent.Accept)
        consent.saveResult = null

        model.onEvent(ConsentPromptEvent.Reject)

        assertThat(consent.savedChoices).isEqualTo(listOf(choice(true), choice(false)))
        assertThat(model.state.value.isDone).isTrue()
        assertThat(model.state.value.error).isNull()
    }

    // AC-22
    @Test
    fun aRefusedTextVersionReloadsTheTextAndSaysSo() = runTest {
        consent.saveResult = Result.Failure(DataError.Remote.UNPROCESSABLE)
        val model = createModel()
        consent.textsProvider = { tag -> Result.Success(FakeConsentRepository.texts(version = 2, language = tag)) }

        model.onEvent(ConsentPromptEvent.Accept)

        val state = model.state.value
        assertThat(state.isDone).isFalse()
        assertThat(state.error).isEqualTo(UiText.Resource(Res.string.consent_error_text_updated))
        assertThat((state.texts as ConsentTextsLoad.Loaded).texts.version).isEqualTo(2)
    }

    // AC-7
    @Test
    fun withoutALoadedTextNoDecisionCanBeMade() = runTest {
        consent.textsProvider = { Result.Failure(DataError.Remote.NO_INTERNET) }
        val model = createModel()

        model.onEvent(ConsentPromptEvent.Accept)
        model.onEvent(ConsentPromptEvent.Reject)

        assertThat(model.state.value.canDecide).isFalse()
        assertThat(model.state.value.texts).isInstanceOf<ConsentTextsLoad.Failed>()
        assertThat(consent.savedChoices.size).isEqualTo(0)
    }

    // AC-7
    @Test
    fun retryingAfterAFailedTextLoadEnablesTheButtons() = runTest {
        consent.textsProvider = { Result.Failure(DataError.Remote.NO_INTERNET) }
        val model = createModel()
        consent.textsProvider = { tag -> Result.Success(FakeConsentRepository.texts(language = tag)) }

        model.onEvent(ConsentPromptEvent.RetryTexts)

        assertThat(model.state.value.canDecide).isTrue()
    }

    // AC-8
    @Test
    fun aSecondAnswerWhileSavingIsIgnored() = runTest {
        consent.saveGate = CompletableDeferred()
        val model = createModel()

        model.onEvent(ConsentPromptEvent.Accept)
        model.onEvent(ConsentPromptEvent.Reject)

        assertThat(model.state.value.isSaving).isTrue()
        assertThat(model.state.value.canDecide).isFalse()
        assertThat(consent.savedChoices).isEqualTo(listOf(choice(true)))
    }
}
