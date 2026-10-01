package com.anksoft.myapplication

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.anksoft.myapplication.core.config.AppConfig
import com.anksoft.myapplication.core.di.initKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // App() resolves dependencies through Koin, so it must be started first.
    // Web has no backend yet, so it always runs in demo mode.
    initKoin(config = AppConfig.Demo)
    ComposeViewport {
        App()
    }
}
