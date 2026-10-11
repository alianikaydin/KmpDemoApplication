package com.anksoft.myapplication

import androidx.compose.ui.window.ComposeUIViewController
import com.anksoft.myapplication.core.network.mock.DemoLaunchArguments
import org.koin.mp.KoinPlatform
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    // Demo builds only (Maestro passes `-kmpDemoVersionPolicy <value>`); a build with a real backend ignores it.
    DemoLaunchArguments.apply(
        koin = KoinPlatform.getKoin(),
        versionPolicy = NSUserDefaults.standardUserDefaults.stringForKey(DemoLaunchArguments.VERSION_POLICY)
    )
    return ComposeUIViewController { App() }
}
