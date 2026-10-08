package com.anksoft.myapplication

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import com.anksoft.myapplication.core.preferences.AppPreferences
import com.anksoft.myapplication.core.presentation.locale.LocaleKeyedContent
import com.anksoft.myapplication.core.presentation.locale.ProvideAppLocale
import com.anksoft.myapplication.core.session.SessionExpiry
import com.anksoft.myapplication.core.storage.SessionManager
import com.anksoft.myapplication.features.auth.presentation.login.LoginScreen
import com.anksoft.myapplication.features.auth.presentation.signup.SignUpScreen
import com.anksoft.myapplication.features.home.presentation.HomeScreen
import org.koin.compose.koinInject

@Composable
@Preview
fun App() {
    val sessionManager = koinInject<SessionManager>()
    val sessionExpiry = koinInject<SessionExpiry>()
    val appPreferences = koinInject<AppPreferences>()
    val language by appPreferences.language.collectAsState()
    // Remembered once: App recomposes when the language changes and must not hand the
    // Navigator a new initial screen.
    val initialScreen = remember {
        if (sessionManager.getToken() != null) HomeScreen() else LoginScreen()
    }

    ProvideAppLocale(language) {
        MaterialTheme {
            Navigator(initialScreen) { navigator ->
                // An expired session leaves no screen worth keeping: back to Login, no way back.
                LaunchedEffect(navigator) {
                    // The event is not replayed, so one that fired while nothing was collecting
                    // (for example during an Activity restart) is caught by this check.
                    val current = navigator.lastItem
                    if (sessionManager.getToken() == null && current !is LoginScreen && current !is SignUpScreen) {
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
        }
    }
}
