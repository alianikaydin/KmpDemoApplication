package com.anksoft.myapplication.core.network.mock

import com.anksoft.myapplication.core.config.AppConfig
import org.koin.core.Koin

/**
 * Launch arguments read only in demo mode (Maestro `launchApp: arguments:`). Builds without the mock
 * backend ignore them, so they cannot change what a real build does.
 */
object DemoLaunchArguments {

    /** Names a [DemoVersionPolicy]: `none`, `optional` or `required`. */
    const val VERSION_POLICY = "kmpDemoVersionPolicy"

    /**
     * Hands [versionPolicy] to the mock backend when this build runs in demo mode; does nothing
     * otherwise. Call it before the first screen, because the update check runs at start-up.
     */
    fun apply(koin: Koin, versionPolicy: String?) {
        if (versionPolicy == null || !koin.get<AppConfig>().useMockBackend) return
        koin.get<MockAuthServer>().useVersionPolicy(DemoVersionPolicy.parse(versionPolicy))
    }
}
