package com.anksoft.myapplication.features.settings.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.anksoft.myapplication.core.preferences.AppLanguage
import com.anksoft.myapplication.core.presentation.asString
import com.anksoft.myapplication.features.auth.presentation.login.LoginScreen
import com.anksoft.myapplication.features.consent.presentation.PrivacyPolicyLink
import com.anksoft.myapplication.features.profile.domain.model.AccountProfile
import com.anksoft.myapplication.features.profile.domain.model.ProfileState
import myapplication.shared.generated.resources.Res
import myapplication.shared.generated.resources.common_back
import myapplication.shared.generated.resources.common_retry
import myapplication.shared.generated.resources.consent_state_off
import myapplication.shared.generated.resources.consent_state_on
import myapplication.shared.generated.resources.consent_status_load_failed
import myapplication.shared.generated.resources.language_system_default
import myapplication.shared.generated.resources.settings_environment
import myapplication.shared.generated.resources.settings_language
import myapplication.shared.generated.resources.settings_consent_toggle_label
import myapplication.shared.generated.resources.settings_logout
import myapplication.shared.generated.resources.settings_privacy_title
import myapplication.shared.generated.resources.settings_test_crash
import myapplication.shared.generated.resources.settings_profile_loading
import myapplication.shared.generated.resources.settings_profile_stale_note
import myapplication.shared.generated.resources.settings_profile_unavailable
import myapplication.shared.generated.resources.settings_title
import myapplication.shared.generated.resources.settings_version
import org.jetbrains.compose.resources.stringResource

class SettingsScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<SettingsScreenModel>()
        val state by screenModel.state.collectAsState()

        LaunchedEffect(state.isLoggedOut) {
            if (state.isLoggedOut) {
                navigator.replaceAll(LoginScreen())
            }
        }

        SettingsContent(
            state = state,
            onEvent = screenModel::onEvent,
            onBackClick = { navigator.pop() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    state: SettingsState,
    onEvent: (SettingsEvent) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag(SettingsTestTags.BACK_BUTTON)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.common_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(100.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            ProfileSection(
                profile = state.profile,
                onRetryClick = { onEvent(SettingsEvent.RetryProfile) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(16.dp))

            LanguagePicker(
                selected = state.selectedLanguage,
                onSelect = { onEvent(SettingsEvent.LanguageSelect(it)) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(16.dp))

            PrivacySection(
                privacy = state.privacy,
                onEvent = onEvent,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.settings_environment, state.environmentName),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(SettingsTestTags.ENVIRONMENT)
            )
            Text(
                text = stringResource(Res.string.settings_version, state.versionName, state.versionCode),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(SettingsTestTags.VERSION)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onEvent(SettingsEvent.Logout) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SettingsTestTags.LOGOUT_BUTTON),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                enabled = !state.isLoading
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onError
                    )
                } else {
                    Text(stringResource(Res.string.settings_logout))
                }
            }

            // Last, below the logout button, so the layout above stays as it was. Only dev and stage
            // builds bind a trigger; in prod there is no button and nothing to call.
            if (state.showTestCrash) {
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { onEvent(SettingsEvent.TestCrash) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(SettingsTestTags.TEST_CRASH)
                ) {
                    Text(stringResource(Res.string.settings_test_crash))
                }
            }
        }
    }
}

/**
 * The account's name and e-mail, or what stands in for them: a loading indicator, a note that the shown
 * details may be outdated, or an empty state. The rest of the screen works in every state (AC-1..AC-8).
 */
@Composable
private fun ProfileSection(
    profile: ProfileState,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        when (profile) {
            ProfileState.SignedOut -> Unit

            ProfileState.Loading -> {
                val loadingDescription = stringResource(Res.string.settings_profile_loading)
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(32.dp)
                        .semantics { contentDescription = loadingDescription }
                        .testTag(SettingsTestTags.PROFILE_LOADING)
                )
            }

            is ProfileState.Available -> {
                // Without a name the e-mail stands alone on one line (AC-1).
                profile.profile.name?.let { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.testTag(SettingsTestTags.PROFILE_NAME)
                    )
                }
                Text(
                    text = profile.profile.email,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(SettingsTestTags.PROFILE_EMAIL)
                )
                if (profile.isStale) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(Res.string.settings_profile_stale_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag(SettingsTestTags.PROFILE_STALE_NOTE)
                    )
                    ProfileRetryButton(onClick = onRetryClick)
                }
            }

            ProfileState.Unavailable -> {
                Text(
                    text = stringResource(Res.string.settings_profile_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag(SettingsTestTags.PROFILE_UNAVAILABLE)
                )
                ProfileRetryButton(onClick = onRetryClick)
            }
        }
    }
}

@Composable
private fun ProfileRetryButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag(SettingsTestTags.PROFILE_RETRY)
    ) {
        Text(stringResource(Res.string.common_retry))
    }
}

/** The switch for optional data collection (AC-13..AC-16). */
@Composable
private fun PrivacySection(
    privacy: PrivacyState,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(Res.string.settings_privacy_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )

        // The backend's own label once it is loaded; the app's generic one otherwise.
        val label = privacy.label ?: stringResource(Res.string.settings_consent_toggle_label)
        val stateDescription = stringResource(
            if (privacy.isChecked) Res.string.consent_state_on else Res.string.consent_state_off
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(
                    value = privacy.isChecked,
                    enabled = privacy.isToggleEnabled,
                    role = Role.Switch,
                    onValueChange = { onEvent(SettingsEvent.ConsentToggle(it)) }
                )
                .semantics { this.stateDescription = stateDescription }
                .testTag(SettingsTestTags.CONSENT_TOGGLE),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(12.dp))
            // The row owns the interaction; the switch only draws the state.
            Switch(checked = privacy.isChecked, onCheckedChange = null)
        }

        PrivacyPolicyLink(url = privacy.policyUrl, testTag = SettingsTestTags.PRIVACY_POLICY)

        if (privacy.showRetry) {
            Text(
                text = stringResource(Res.string.consent_status_load_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            TextButton(
                onClick = { onEvent(SettingsEvent.RetryConsent) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(SettingsTestTags.CONSENT_RETRY)
            ) {
                Text(stringResource(Res.string.common_retry))
            }
        }

        privacy.error?.let { error ->
            Text(
                text = error.asString(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}

@Composable
private fun LanguagePicker(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit
) {
    val systemDefaultLabel = stringResource(Res.string.language_system_default)
    val options = AppLanguage.entries.map { language ->
        RadioOption(
            value = language,
            // Languages are named in themselves; only "System default" is translated.
            label = language.nativeName ?: systemDefaultLabel,
            testTag = language.testTag()
        )
    }
    SettingsRadioGroup(
        title = stringResource(Res.string.settings_language),
        options = options,
        selected = selected,
        onSelect = onSelect
    )
}

private fun AppLanguage.testTag(): String = when (this) {
    AppLanguage.SYSTEM -> SettingsTestTags.LANGUAGE_SYSTEM
    AppLanguage.TURKISH -> SettingsTestTags.LANGUAGE_TURKISH
    AppLanguage.ENGLISH -> SettingsTestTags.LANGUAGE_ENGLISH
}

@Preview
@Composable
private fun SettingsContentPreview() {
    SettingsContent(
        state = SettingsState(
            profile = ProfileState.Available(
                AccountProfile(userId = "u1", email = "ali@example.com", name = "Ali"),
                isStale = false
            ),
            environmentName = "DEV",
            versionName = "1.0",
            versionCode = 1,
            selectedLanguage = AppLanguage.TURKISH
        ),
        onEvent = {},
        onBackClick = {}
    )
}

@Preview
@Composable
private fun SettingsContentWithTestCrashPreview() {
    SettingsContent(
        state = SettingsState(
            environmentName = "STAGE",
            versionName = "1.0",
            versionCode = 1,
            showTestCrash = true
        ),
        onEvent = {},
        onBackClick = {}
    )
}

@Preview
@Composable
private fun SettingsContentStaleProfilePreview() {
    SettingsContent(
        state = SettingsState(
            profile = ProfileState.Available(
                AccountProfile(userId = "u1", email = "ali@example.com", name = null),
                isStale = true
            ),
            environmentName = "DEV",
            versionName = "1.0",
            versionCode = 1
        ),
        onEvent = {},
        onBackClick = {}
    )
}

@Preview
@Composable
private fun SettingsContentUnavailableProfilePreview() {
    SettingsContent(
        state = SettingsState(
            profile = ProfileState.Unavailable,
            environmentName = "DEV",
            versionName = "1.0",
            versionCode = 1
        ),
        onEvent = {},
        onBackClick = {}
    )
}
