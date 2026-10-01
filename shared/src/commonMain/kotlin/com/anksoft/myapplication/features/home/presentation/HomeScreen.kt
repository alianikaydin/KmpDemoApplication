package com.anksoft.myapplication.features.home.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow

import com.anksoft.myapplication.features.settings.presentation.SettingsScreen

class HomeScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        Scaffold(
            topBar = {
                TopAppBar(title = { Text("Home", modifier = Modifier.testTag(HomeTestTags.TITLE)) })
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
        text = "Welcome to the Dashboard!",
        style = MaterialTheme.typography.headlineMedium
    )
}

@Composable
fun HomeActionSection(navigator: Navigator) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(onClick = { navigator.push(SettingsScreen()) }) {
            Text("Go to Settings")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedButton(onClick = { navigator.push(SettingsScreen()) }) {
            Text("Profile")
        }
    }
}
