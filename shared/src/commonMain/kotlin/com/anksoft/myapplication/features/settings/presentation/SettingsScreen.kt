package com.anksoft.myapplication.features.settings.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.anksoft.myapplication.core.preferences.AppLanguage
import com.anksoft.myapplication.core.preferences.ThemeMode
import com.anksoft.myapplication.core.presentation.theme.AppTheme
import com.anksoft.myapplication.features.auth.presentation.login.LoginScreen
import myapplication.shared.generated.resources.Res
import myapplication.shared.generated.resources.common_back
import myapplication.shared.generated.resources.language_system_default
import myapplication.shared.generated.resources.settings_environment
import myapplication.shared.generated.resources.settings_language
import myapplication.shared.generated.resources.settings_logout
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

            Text(
                text = state.userName,
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = state.userEmail,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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

            ThemePicker(
                selected = state.selectedThemeMode,
                onSelect = { onEvent(SettingsEvent.ThemeModeSelect(it)) }
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
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
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
            environmentName = "DEV",
            versionName = "1.0",
            versionCode = 1,
            selectedLanguage = AppLanguage.TURKISH,
            selectedThemeMode = ThemeMode.DARK
        ),
        onEvent = {},
        onBackClick = {}
    )
}

@Preview
@Composable
private fun SettingsContentDarkPreview() {
    AppTheme(darkTheme = true) {
        SettingsContent(
            state = SettingsState(
                environmentName = "DEV",
                versionName = "1.0",
                versionCode = 1,
                selectedThemeMode = ThemeMode.DARK
            ),
            onEvent = {},
            onBackClick = {}
        )
    }
}
