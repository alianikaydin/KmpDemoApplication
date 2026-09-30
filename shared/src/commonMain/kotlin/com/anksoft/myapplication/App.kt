package com.anksoft.myapplication

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import com.anksoft.myapplication.core.storage.SessionManager
import com.anksoft.myapplication.features.auth.presentation.login.LoginScreen
import com.anksoft.myapplication.features.home.presentation.HomeScreen
import org.koin.compose.koinInject

@Composable
@Preview
fun App() {
    val sessionManager = koinInject<SessionManager>()
    val isLoggedIn = remember { sessionManager.getToken() != null }
    val initialScreen = if (isLoggedIn) HomeScreen() else LoginScreen()

    MaterialTheme {
        Navigator(initialScreen) { navigator ->
            SlideTransition(navigator)
        }
    }
}
