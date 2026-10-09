package com.anksoft.myapplication.features.auth.presentation.signup

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.preferences.AppLanguage
import com.anksoft.myapplication.core.preferences.ContentLanguage
import com.anksoft.myapplication.core.preferences.FakeAppPreferences
import com.anksoft.myapplication.core.presentation.UiText
import com.anksoft.myapplication.features.auth.FakeAuthRepository
import com.anksoft.myapplication.features.auth.domain.UserDataValidator
import com.anksoft.myapplication.features.consent.FakeConsentRepository
import com.anksoft.myapplication.features.consent.domain.model.ConsentChoice
import com.anksoft.myapplication.features.consent.domain.usecase.ConsentTextsLoad
import com.anksoft.myapplication.features.consent.domain.usecase.ObserveConsentTextsUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import myapplication.shared.generated.resources.Res
import myapplication.shared.generated.resources.consent_error_text_updated
import myapplication.shared.generated.resources.error_email_already_registered
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class SignUpScreenModelTest {

    private lateinit var auth: FakeAuthRepository
    private lateinit var consent: FakeConsentRepository
    private lateinit var preferences: FakeAppPreferences
    private lateinit var model: SignUpScreenModel

    @BeforeTest
    fun setUp() {
        // screenModelScope is Main-dispatched, so Main must be replaced.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        auth = FakeAuthRepository()
        consent = FakeConsentRepository()
        preferences = FakeAppPreferences(AppLanguage.ENGLISH)
        model = newModel()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newModel() = SignUpScreenModel(
        auth,
        UserDataValidator(),
        ObserveConsentTextsUseCase(consent, ContentLanguage(preferences) { "en" })
    )

    private fun fillValidForm() {
        model.onEvent(SignUpEvent.EmailChanged("New@Example.com"))
        model.onEvent(SignUpEvent.PasswordChanged("Passw0rdX"))
        model.onEvent(SignUpEvent.ConfirmPasswordChanged("Passw0rdX"))
    }

    private fun loadedTexts() = (model.state.value.consentTexts as ConsentTextsLoad.Loaded).texts

    // AC-1: the box is unchecked and the backend text is shown.
    @Test
    fun theConsentBoxStartsUncheckedWithTheLoadedText() {
        val state = model.state.value

        assertThat(state.consentChecked).isFalse()
        assertThat(state.consentTexts).isEqualTo(ConsentTextsLoad.Loaded(FakeConsentRepository.texts()))
        assertThat(consent.textRequests).isEqualTo(listOf("en"))
    }

    // AC-2: consent is optional, so an unchecked box does not block the submit button.
    @Test
    fun anUncheckedBoxDoesNotBlockSubmitting() {
        fillValidForm()

        assertThat(model.state.value.canSubmit).isTrue()
    }

    // AC-2: unchecked registers with an explicit denial for the shown text.
    @Test
    fun registeringUncheckedSendsADenialForTheShownText() = runTest {
        fillValidForm()

        model.onEvent(SignUpEvent.SignUpClicked)

        assertThat(auth.registerCalls).hasSize(1)
        assertThat(auth.registerCalls.single().first).isEqualTo("new@example.com")
        assertThat(auth.registerCalls.single().third)
            .isEqualTo(ConsentChoice(granted = false, textVersion = 1, textLanguage = "en"))
        assertThat(model.state.value.isSignedUp).isTrue()
    }

    // AC-3: checked registers with a grant for the shown text version and language.
    @Test
    fun registeringCheckedSendsAGrantForTheShownText() = runTest {
        fillValidForm()
        model.onEvent(SignUpEvent.ConsentCheckedChange(true))

        model.onEvent(SignUpEvent.SignUpClicked)

        assertThat(auth.registerCalls.single().third)
            .isEqualTo(ConsentChoice(granted = true, textVersion = 1, textLanguage = "en"))
    }

    // AC-4: without a loaded text the box cannot be checked and no decision is sent.
    @Test
    fun withoutALoadedTextTheBoxIsInertAndNoDecisionIsSent() = runTest {
        consent.textsProvider = { Result.Failure(DataError.Remote.NO_INTERNET) }
        model = newModel()
        fillValidForm()

        model.onEvent(SignUpEvent.ConsentCheckedChange(true))
        model.onEvent(SignUpEvent.SignUpClicked)

        assertThat(model.state.value.consentTexts)
            .isEqualTo(ConsentTextsLoad.Failed(DataError.Remote.NO_INTERNET))
        assertThat(model.state.value.consentChecked).isFalse()
        assertThat(auth.registerCalls).hasSize(1)
        assertThat(auth.registerCalls.single().third).isNull()
        assertThat(model.state.value.isSignedUp).isTrue()
    }

    // AC-4: retrying after a failed load shows the text.
    @Test
    fun retryingAfterAFailedLoadShowsTheText() = runTest {
        consent.textsProvider = { Result.Failure(DataError.Remote.NO_INTERNET) }
        model = newModel()
        consent.textsProvider = { tag -> Result.Success(FakeConsentRepository.texts(language = tag)) }

        model.onEvent(SignUpEvent.RetryConsentTexts)

        assertThat(model.state.value.consentTexts).isInstanceOf<ConsentTextsLoad.Loaded>()
    }

    // AC-5: after a failed registration the choice is kept and sent again unchanged.
    @Test
    fun theChoiceSurvivesAFailedRegistration() = runTest {
        val success = auth.registerResult
        auth.registerResult = Result.Failure(DataError.Remote.NO_INTERNET)
        fillValidForm()
        model.onEvent(SignUpEvent.ConsentCheckedChange(true))
        model.onEvent(SignUpEvent.SignUpClicked)

        assertThat(model.state.value.consentChecked).isTrue()
        assertThat(model.state.value.isSignedUp).isFalse()

        auth.registerResult = success
        model.onEvent(SignUpEvent.SignUpClicked)

        assertThat(auth.registerCalls).hasSize(2)
        assertThat(auth.registerCalls[1].third).isEqualTo(auth.registerCalls[0].third)
        assertThat(auth.registerCalls[1].third?.granted).isEqualTo(true)
    }

    // AC-22: a refused text version resets the box, reloads the text and says why.
    @Test
    fun aRefusedTextVersionResetsTheBoxAndReloadsTheText() = runTest {
        fillValidForm()
        model.onEvent(SignUpEvent.ConsentCheckedChange(true))
        auth.registerResult = Result.Failure(DataError.Remote.UNPROCESSABLE)
        consent.textsProvider = { tag -> Result.Success(FakeConsentRepository.texts(version = 2, language = tag)) }

        model.onEvent(SignUpEvent.SignUpClicked)

        val state = model.state.value
        assertThat(state.consentChecked).isFalse()
        assertThat(state.isLoading).isFalse()
        assertThat(state.formError).isEqualTo(UiText.Resource(Res.string.consent_error_text_updated))
        assertThat(loadedTexts().version).isEqualTo(2)
        assertThat(consent.textRequests).hasSize(2)
    }

    // S7: a check given for an old text version does not carry over to a new one.
    @Test
    fun aCheckDoesNotCarryOverToANewTextVersion() = runTest {
        model.onEvent(SignUpEvent.ConsentCheckedChange(true))
        consent.textsProvider = { tag -> Result.Success(FakeConsentRepository.texts(version = 2, language = tag)) }

        model.onEvent(SignUpEvent.RetryConsentTexts)

        assertThat(loadedTexts().version).isEqualTo(2)
        assertThat(model.state.value.consentChecked).isFalse()
    }

    // AC-24: changing the app language reloads the text; the same version keeps the check.
    @Test
    fun changingTheLanguageReloadsTheTextAndKeepsTheCheck() = runTest {
        model.onEvent(SignUpEvent.ConsentCheckedChange(true))

        preferences.setLanguage(AppLanguage.TURKISH)

        assertThat(consent.textRequests).isEqualTo(listOf("en", "tr"))
        assertThat(loadedTexts().language).isEqualTo("tr")
        assertThat(model.state.value.consentChecked).isTrue()
    }

    @Test
    fun aTakenEmailIsReportedAsConflict() = runTest {
        auth.registerResult = Result.Failure(DataError.Remote.CONFLICT)
        fillValidForm()

        model.onEvent(SignUpEvent.SignUpClicked)

        assertThat(model.state.value.formError)
            .isEqualTo(UiText.Resource(Res.string.error_email_already_registered))
        assertThat(model.state.value.consentChecked).isFalse()
    }

    @Test
    fun invalidInputDoesNotReachTheRepository() = runTest {
        model.onEvent(SignUpEvent.EmailChanged("not-an-email"))
        model.onEvent(SignUpEvent.PasswordChanged("short"))
        model.onEvent(SignUpEvent.ConfirmPasswordChanged("other"))

        model.onEvent(SignUpEvent.SignUpClicked)

        assertThat(auth.registerCalls).hasSize(0)
        assertThat(model.state.value.emailError).isInstanceOf<UiText.Resource>()
    }

    @Test
    fun aSecondSubmitWhileRegisteringIsIgnored() = runTest {
        auth.registerGate = CompletableDeferred()
        fillValidForm()

        model.onEvent(SignUpEvent.SignUpClicked)
        model.onEvent(SignUpEvent.SignUpClicked)

        assertThat(auth.registerCalls).hasSize(1)
        assertThat(model.state.value.isLoading).isTrue()
    }
}
