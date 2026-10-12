package com.anksoft.myapplication.features.settings.presentation

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.anksoft.myapplication.features.profile.domain.model.AccountProfile
import com.anksoft.myapplication.features.profile.domain.model.ProfileState
import kotlin.test.Test
import kotlin.test.assertEquals
import myapplication.shared.generated.resources.Res
import myapplication.shared.generated.resources.settings_profile_loading
import myapplication.shared.generated.resources.settings_profile_stale_note
import myapplication.shared.generated.resources.settings_profile_unavailable
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalTestApi::class)
class SettingsProfileUiTest {

    private val named = AccountProfile(userId = "u1", email = "ali@example.com", name = "Ali")
    private val nameless = AccountProfile(userId = "u1", email = "ali@example.com", name = null)

    private fun stateWith(profile: ProfileState) = SettingsState(
        profile = profile,
        environmentName = "DEV",
        versionName = "1.0",
        versionCode = 1,
    )

    private val allProfileTags = listOf(
        SettingsTestTags.PROFILE_LOADING,
        SettingsTestTags.PROFILE_NAME,
        SettingsTestTags.PROFILE_EMAIL,
        SettingsTestTags.PROFILE_STALE_NOTE,
        SettingsTestTags.PROFILE_UNAVAILABLE,
        SettingsTestTags.PROFILE_RETRY,
    )

    // AC-4, AC-25: the loading indicator is announced.
    @Test
    fun loadingShowsAnIndicatorThatDescribesItself() = runComposeUiTest {
        var expected = ""
        setContent {
            expected = stringResource(Res.string.settings_profile_loading)
            SettingsContent(state = stateWith(ProfileState.Loading), onEvent = {}, onBackClick = {})
        }
        onNodeWithTag(SettingsTestTags.PROFILE_LOADING).assertExists().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(expected))
        )
        onNodeWithTag(SettingsTestTags.PROFILE_EMAIL).assertDoesNotExist()
    }

    // AC-1: a profile with a name shows the name and the e-mail, and no note.
    @Test
    fun availableProfileShowsNameAndEmail() = runComposeUiTest {
        setContent {
            SettingsContent(
                state = stateWith(ProfileState.Available(named, isStale = false)),
                onEvent = {},
                onBackClick = {}
            )
        }
        onNodeWithTag(SettingsTestTags.PROFILE_NAME).assertTextEquals("Ali")
        onNodeWithTag(SettingsTestTags.PROFILE_EMAIL).assertTextEquals("ali@example.com")
        onNodeWithTag(SettingsTestTags.PROFILE_STALE_NOTE).assertDoesNotExist()
        onNodeWithTag(SettingsTestTags.PROFILE_RETRY).assertDoesNotExist()
    }

    // AC-1: without a name only the e-mail is shown.
    @Test
    fun profileWithoutANameShowsOnlyTheEmail() = runComposeUiTest {
        setContent {
            SettingsContent(
                state = stateWith(ProfileState.Available(nameless, isStale = false)),
                onEvent = {},
                onBackClick = {}
            )
        }
        onNodeWithTag(SettingsTestTags.PROFILE_NAME).assertDoesNotExist()
        onNodeWithTag(SettingsTestTags.PROFILE_EMAIL).assertTextEquals("ali@example.com")
    }

    // AC-5, AC-8: a stale profile keeps its details, adds the note and offers a retry.
    @Test
    fun staleProfileShowsTheNoteAndRetrySendsTheRetryEvent() = runComposeUiTest {
        var expected = ""
        val events = mutableListOf<SettingsEvent>()
        setContent {
            expected = stringResource(Res.string.settings_profile_stale_note)
            SettingsContent(
                state = stateWith(ProfileState.Available(named, isStale = true)),
                onEvent = { events += it },
                onBackClick = {}
            )
        }
        onNodeWithTag(SettingsTestTags.PROFILE_EMAIL).assertTextEquals("ali@example.com")
        onNodeWithTag(SettingsTestTags.PROFILE_STALE_NOTE).assertTextEquals(expected)

        onNodeWithTag(SettingsTestTags.PROFILE_RETRY).performScrollTo().performClick()

        assertEquals(listOf<SettingsEvent>(SettingsEvent.RetryProfile), events)
    }

    // AC-7, AC-8: nothing to show and no way to get it: an empty state with a retry.
    @Test
    fun unavailableProfileShowsTheEmptyStateAndRetry() = runComposeUiTest {
        var expected = ""
        val events = mutableListOf<SettingsEvent>()
        setContent {
            expected = stringResource(Res.string.settings_profile_unavailable)
            SettingsContent(
                state = stateWith(ProfileState.Unavailable),
                onEvent = { events += it },
                onBackClick = {}
            )
        }
        onNodeWithTag(SettingsTestTags.PROFILE_UNAVAILABLE).assertTextEquals(expected)
        onNodeWithTag(SettingsTestTags.PROFILE_EMAIL).assertDoesNotExist()

        onNodeWithTag(SettingsTestTags.PROFILE_RETRY).performScrollTo().performClick()

        assertEquals(listOf<SettingsEvent>(SettingsEvent.RetryProfile), events)
    }

    // AC-10: without a session no profile element is on screen.
    @Test
    fun signedOutShowsNoProfileElements() = runComposeUiTest {
        setContent {
            SettingsContent(state = stateWith(ProfileState.SignedOut), onEvent = {}, onBackClick = {})
        }
        allProfileTags.forEach { tag -> onNodeWithTag(tag).assertDoesNotExist() }
    }
}
