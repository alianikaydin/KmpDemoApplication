package com.anksoft.myapplication.features.home.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.anksoft.myapplication.features.consent.presentation.prompt.ConsentPromptScreen
import com.anksoft.myapplication.features.settings.presentation.SettingsScreen
import myapplication.shared.generated.resources.Res
import myapplication.shared.generated.resources.home_go_to_settings
import myapplication.shared.generated.resources.home_profile
import myapplication.shared.generated.resources.home_title
import myapplication.shared.generated.resources.home_welcome
import org.jetbrains.compose.resources.stringResource

class HomeScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = koinScreenModel<HomeScreenModel>()
        val state by screenModel.state.collectAsState()

        LaunchedEffect(state.showConsentPrompt) {
            if (state.showConsentPrompt) {
                navigator.push(ConsentPromptScreen())
                screenModel.onEvent(HomeEvent.ConsentPromptOpened)
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(Res.string.home_title),
                            modifier = Modifier.testTag(HomeTestTags.TITLE)
                        )
                    }
                )
            }
        ) { padding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                val isWide = maxWidth > 600.dp
                
                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HomeInfoSection()
                        HomeActionSection(navigator)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        HomeInfoSection()
                        Spacer(modifier = Modifier.height(32.dp))
                        HomeActionSection(navigator)
                    }
                }
            }
        }
    }
}

@Composable
fun HomeInfoSection() {
    Text(
        text = stringResource(Res.string.home_welcome),
        style = MaterialTheme.typography.headlineMedium
    )
}

@Composable
fun HomeActionSection(navigator: Navigator) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = { navigator.push(SettingsScreen()) },
            modifier = Modifier.testTag(HomeTestTags.SETTINGS_BUTTON)
        ) {
            Text(stringResource(Res.string.home_go_to_settings))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedButton(
            onClick = { navigator.push(SettingsScreen()) },
            modifier = Modifier.testTag(HomeTestTags.PROFILE_BUTTON)
        ) {
            Text(stringResource(Res.string.home_profile))
        }
    }
}
