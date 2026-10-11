package com.anksoft.myapplication.features.settings.presentation

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
import kotlin.test.Test
import kotlin.test.assertEquals
import myapplication.shared.generated.resources.Res
import myapplication.shared.generated.resources.consent_state_off
import myapplication.shared.generated.resources.consent_state_on
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalTestApi::class)
class SettingsConsentUiTest {

    private fun stateWith(checked: Boolean) = SettingsState(
        environmentName = "DEV",
        versionName = "1.0",
        versionCode = 1,
        privacy = PrivacyState(isChecked = checked, isToggleEnabled = true, label = "Optional data collection")
    )

    private fun stateDescriptionIs(expected: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expected)

    // AC-14: a granted decision shows the switch on and says so.
    @Test
    fun switchIsOnAndDescribesItselfWhenGranted() = runComposeUiTest {
        var expected = ""
        setContent {
            expected = stringResource(Res.string.consent_state_on)
            SettingsContent(state = stateWith(checked = true), onEvent = {}, onBackClick = {})
        }
        onNodeWithTag(SettingsTestTags.CONSENT_TOGGLE).assertIsOn().assert(stateDescriptionIs(expected))
    }

    // AC-14: no decision or a denied one shows the switch off and says so.
    @Test
    fun switchIsOffAndDescribesItselfWhenNotGranted() = runComposeUiTest {
        var expected = ""
        setContent {
            expected = stringResource(Res.string.consent_state_off)
            SettingsContent(state = stateWith(checked = false), onEvent = {}, onBackClick = {})
        }
        onNodeWithTag(SettingsTestTags.CONSENT_TOGGLE).assertIsOff().assert(stateDescriptionIs(expected))
    }

    // AC-15: tapping the row asks for the opposite value.
    @Test
    fun tappingTheRowSendsTheOppositeValue() = runComposeUiTest {
        val events = mutableListOf<SettingsEvent>()
        setContent {
            SettingsContent(state = stateWith(checked = false), onEvent = { events += it }, onBackClick = {})
        }
        onNodeWithTag(SettingsTestTags.CONSENT_TOGGLE).performScrollTo().performClick()
        assertEquals(listOf<SettingsEvent>(SettingsEvent.ConsentToggle(true)), events)
    }
}
