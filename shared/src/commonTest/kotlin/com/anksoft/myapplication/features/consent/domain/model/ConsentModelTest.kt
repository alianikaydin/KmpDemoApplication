package com.anksoft.myapplication.features.consent.domain.model

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.anksoft.myapplication.core.consent.OptionalDataConsent
import com.anksoft.myapplication.features.consent.FakeConsentRepository
import kotlin.test.Test

class ConsentModelTest {

    // AC-25
    @Test
    fun onlyAConfirmedCurrentGrantAllowsCollection() {
        val expected = mapOf(
            ConsentStatus.SignedOut to OptionalDataConsent.UNKNOWN,
            ConsentStatus.Loading to OptionalDataConsent.UNKNOWN,
            ConsentStatus.Unavailable to OptionalDataConsent.UNKNOWN,
            ConsentStatus.Undecided to OptionalDataConsent.UNKNOWN,
            ConsentStatus.Granted() to OptionalDataConsent.GRANTED,
            ConsentStatus.Granted(reconsentRequired = true) to OptionalDataConsent.UNKNOWN,
            ConsentStatus.Granted(suppressed = true) to OptionalDataConsent.DENIED,
            ConsentStatus.Granted(reconsentRequired = true, suppressed = true) to OptionalDataConsent.DENIED,
            ConsentStatus.Denied to OptionalDataConsent.DENIED,
        )

        expected.forEach { (status, facade) ->
            assertThat(status.toOptionalDataConsent()).isEqualTo(facade)
        }
    }

    // AC-7, AC-23
    @Test
    fun thePromptIsDueForNoDecisionAndForAGrantThatNeedsReconsent() {
        assertThat(ConsentStatus.Undecided.needsPrompt).isTrue()
        assertThat(ConsentStatus.Granted(reconsentRequired = true).needsPrompt).isTrue()

        assertThat(ConsentStatus.SignedOut.needsPrompt).isFalse()
        assertThat(ConsentStatus.Loading.needsPrompt).isFalse()
        assertThat(ConsentStatus.Unavailable.needsPrompt).isFalse()
        assertThat(ConsentStatus.Granted().needsPrompt).isFalse()
        assertThat(ConsentStatus.Granted(reconsentRequired = true, suppressed = true).needsPrompt).isFalse()
        assertThat(ConsentStatus.Denied.needsPrompt).isFalse()
    }

    @Test
    fun anAccountConsentMapsToTheStatusTheScreensRender() {
        assertThat(AccountConsent(ConsentDecision.NONE, null).toStatus()).isEqualTo(ConsentStatus.Undecided)
        assertThat(AccountConsent(ConsentDecision.DENIED, 1).toStatus()).isEqualTo(ConsentStatus.Denied)
        assertThat(AccountConsent(ConsentDecision.GRANTED, 1).toStatus()).isEqualTo(ConsentStatus.Granted())
        assertThat(AccountConsent(ConsentDecision.GRANTED, 1, reconsentRequired = true).toStatus())
            .isEqualTo(ConsentStatus.Granted(reconsentRequired = true))
    }

    // AC-22
    @Test
    fun aChoiceCarriesTheVersionAndLanguageOfTheTextThatWasShown() {
        val texts = FakeConsentRepository.texts(version = 3, language = "tr")

        val choice = ConsentChoice.of(granted = true, texts = texts)

        assertThat(choice).isEqualTo(ConsentChoice(granted = true, textVersion = 3, textLanguage = "tr"))
    }

    @Test
    fun onlyHttpsPolicyLinksAreSafeToOpen() {
        assertThat(FakeConsentRepository.texts(policyUrl = "https://example.com/privacy").hasSafePolicyUrl).isTrue()
        assertThat(FakeConsentRepository.texts(policyUrl = "http://example.com/privacy").hasSafePolicyUrl).isFalse()
        assertThat(FakeConsentRepository.texts(policyUrl = "javascript:alert(1)").hasSafePolicyUrl).isFalse()
        assertThat(FakeConsentRepository.texts(policyUrl = "").hasSafePolicyUrl).isFalse()
    }
}
