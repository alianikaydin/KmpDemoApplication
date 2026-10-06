package com.anksoft.myapplication

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.anksoft.myapplication.core.config.AppConfig
import com.anksoft.myapplication.core.config.AppEnvironment
import com.anksoft.myapplication.core.di.initKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // App() resolves dependencies through Koin, so it must be started first.
    // Environment, backend and demo flag are fixed at build time (see WebBuildEnv).
    val config = AppConfig.create(
        environment = AppEnvironment.parse(WebBuildEnv.ENVIRONMENT),
        backendUrl = WebBuildEnv.BACKEND_URL,
        demoAllowed = WebBuildEnv.DEMO,
        versionName = WebBuildEnv.VERSION_NAME,
        versionCode = WebBuildEnv.VERSION_CODE
    )
    initKoin(config = config)
    ComposeViewport {
        App()
    }
}
