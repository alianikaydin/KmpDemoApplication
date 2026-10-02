package com.anksoft.myapplication

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.anksoft.myapplication.core.config.AppConfig
import com.anksoft.myapplication.core.di.initKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // App() resolves dependencies through Koin, so it must be started first.
    // Demo mode unless built with -PkmpBackendUrl=<url> (see WebBuildEnv).
    initKoin(config = AppConfig.fromBackendUrl(WebBuildEnv.BACKEND_URL))
    ComposeViewport {
        App()
    }
}
