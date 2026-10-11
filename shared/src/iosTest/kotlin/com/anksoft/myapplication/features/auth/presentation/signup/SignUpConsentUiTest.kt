package com.anksoft.myapplication.features.auth.presentation.signup

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.anksoft.myapplication.features.consent.domain.model.ConsentTexts
import com.anksoft.myapplication.features.consent.domain.usecase.ConsentTextsLoad
import kotlin.test.Test
import kotlin.test.assertEquals
import myapplication.shared.generated.resources.Res
import myapplication.shared.generated.resources.consent_state_checked
import myapplication.shared.generated.resources.consent_state_unchecked
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalTestApi::class)
class SignUpConsentUiTest {

    private val texts = ConsentTexts(
        version = 1,
        language = "en",
        label = "Share optional usage data",
        description = "Helps us improve the app.",
        policyUrl = "https://example.com/privacy"
    )

    private fun stateDescriptionIs(expected: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expected)

    // AC-1: the box is unchecked by default and says so.
    @Test
    fun uncheckedBoxIsOffAndDescribesItself() = runComposeUiTest {
        var expected = ""
        setContent {
            expected = stringResource(Res.string.consent_state_unchecked)
            SignUpContent(
                state = SignUpState(consentTexts = ConsentTextsLoad.Loaded(texts), consentChecked = false),
                onEvent = {},
                onBackClick = {}
            )
        }
        onNodeWithTag(SignUpTestTags.CONSENT_CHECKBOX).assertIsOff().assert(stateDescriptionIs(expected))
    }

    // AC-3: a checked box is on and says so.
    @Test
    fun checkedBoxIsOnAndDescribesItself() = runComposeUiTest {
        var expected = ""
        setContent {
            expected = stringResource(Res.string.consent_state_checked)
            SignUpContent(
                state = SignUpState(consentTexts = ConsentTextsLoad.Loaded(texts), consentChecked = true),
                onEvent = {},
                onBackClick = {}
            )
        }
        onNodeWithTag(SignUpTestTags.CONSENT_CHECKBOX).assertIsOn().assert(stateDescriptionIs(expected))
    }

    // AC-3: tapping the row asks for the opposite value.
    @Test
    fun tappingTheRowSendsTheOppositeValue() = runComposeUiTest {
        val events = mutableListOf<SignUpEvent>()
        setContent {
            SignUpContent(
                state = SignUpState(consentTexts = ConsentTextsLoad.Loaded(texts), consentChecked = false),
                onEvent = { events += it },
                onBackClick = {}
            )
        }
        onNodeWithTag(SignUpTestTags.CONSENT_CHECKBOX).performScrollTo().performClick()
        // Tapping also moves focus away from the form fields (EmailFocusLost); only the consent
        // change matters here.
        assertEquals(
            listOf(SignUpEvent.ConsentCheckedChange(true)),
            events.filterIsInstance<SignUpEvent.ConsentCheckedChange>()
        )
    }
}
