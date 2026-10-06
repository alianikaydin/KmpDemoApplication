package com.anksoft.myapplication.core.config

/** Ready-made [AppConfig]s for tests that only need a working config, not a specific rule. */
object TestAppConfigs {

    fun demo(): AppConfig = AppConfig.create(
        environment = AppEnvironment.DEV,
        backendUrl = null,
        demoAllowed = true,
        versionName = "1.0",
        versionCode = 1
    )

    fun remote(url: String): AppConfig = AppConfig.create(
        environment = AppEnvironment.DEV,
        backendUrl = url,
        demoAllowed = false,
        versionName = "1.0",
        versionCode = 1
    )
}
