package com.anksoft.myapplication

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import com.anksoft.myapplication.core.preferences.AppPreferences
import com.anksoft.myapplication.core.presentation.locale.LocaleKeyedContent
import com.anksoft.myapplication.core.presentation.locale.ProvideAppLocale
import com.anksoft.myapplication.core.presentation.theme.AppTheme
import com.anksoft.myapplication.core.presentation.theme.PlatformThemeEffect
import com.anksoft.myapplication.core.session.SessionExpiry
import com.anksoft.myapplication.core.storage.SessionManager
import com.anksoft.myapplication.features.auth.presentation.login.LoginScreen
import com.anksoft.myapplication.features.auth.presentation.signup.SignUpScreen
import com.anksoft.myapplication.features.consent.domain.AccountConsentManager
import com.anksoft.myapplication.features.home.presentation.HomeScreen
import com.anksoft.myapplication.features.update.presentation.AppUpdateController
import com.anksoft.myapplication.features.update.presentation.AppUpdateState
import com.anksoft.myapplication.features.update.presentation.ForceUpdateContent
import com.anksoft.myapplication.features.update.presentation.UpdatePromptDialog
import org.koin.compose.koinInject

@Composable
@Preview
fun App() {
    val sessionManager = koinInject<SessionManager>()
    val sessionExpiry = koinInject<SessionExpiry>()
    val appPreferences = koinInject<AppPreferences>()
    val accountConsentManager = koinInject<AccountConsentManager>()
    val updateController = koinInject<AppUpdateController>()
    val language by appPreferences.language.collectAsState()
    val themeMode by appPreferences.themeMode.collectAsState()
    // Read above ProvideAppLocale so it is the real system configuration.
    val systemInDarkTheme = isSystemInDarkTheme()
    val darkTheme = themeMode.isDark(systemInDarkTheme)
    PlatformThemeEffect(themeMode, darkTheme)
    val updateState by updateController.state.collectAsState()
    // Remembered once: App recomposes when the language changes and must not hand the
    // Navigator a new initial screen.
    val initialScreen = remember {
        if (sessionManager.getToken() != null) HomeScreen() else LoginScreen()
    }

    // Once per process; ON_RESUME is deliberately not used (AC-13). The check never blocks the UI.
    LaunchedEffect(Unit) { updateController.start() }

    // Look at the account's decision again when the app comes back (AC-12); the manager rate-limits it.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { accountConsentManager.onAppResumed() }

    ProvideAppLocale(language) {
        // No key() on the theme: a theme change only recomposes, the back stack survives (AC-5).
        AppTheme(darkTheme = darkTheme) {
            when (val update = updateState) {
                // The required screen replaces the navigator: no way past it (AC-1, AC-2).
                is AppUpdateState.Required -> ForceUpdateContent(update, updateController::onEvent)
                else -> {
                    Navigator(initialScreen) { navigator ->
                        // An expired session leaves no screen worth keeping: back to Login, no way back.
                        LaunchedEffect(navigator) {
                            // The event is not replayed, so one that fired while nothing was collecting
                            // (for example during an Activity restart) is caught by this check.
                            val current = navigator.lastItem
                            val signedOutOnProtectedScreen = sessionManager.getToken() == null &&
                                current !is LoginScreen && current !is SignUpScreen
                            if (signedOutOnProtectedScreen) {
                                navigator.replaceAll(LoginScreen())
                            }
                            sessionExpiry.events.collect { navigator.replaceAll(LoginScreen()) }
                        }
                        SlideTransition(navigator) { screen ->
                            // The key wraps the screen content only, never the Navigator, so the back
                            // stack survives a language change (AC-6).
                            LocaleKeyedContent(language) { screen.Content() }
                        }
                    }
                    if (update is AppUpdateState.Optional) {
                        UpdatePromptDialog(update, updateController::onEvent)
                    }
                }
            }
        }
    }
}
