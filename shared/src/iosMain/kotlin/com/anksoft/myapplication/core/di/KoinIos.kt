package com.anksoft.myapplication.core.di

import com.anksoft.myapplication.core.config.AppConfig
import com.anksoft.myapplication.core.crash.CrashGate
import com.anksoft.myapplication.core.crash.CrashReporter
import com.anksoft.myapplication.core.crash.IosCrashReporter
import com.anksoft.myapplication.core.crash.NativeCrashBridge
import com.anksoft.myapplication.core.crash.PLATFORM_CRASH_REPORTER
import com.anksoft.myapplication.core.crash.TestCrashTrigger
import com.anksoft.myapplication.core.crash.ThrowingTestCrashTrigger
import com.anksoft.myapplication.core.crash.installKotlinCrashHook
import org.koin.core.KoinApplication
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * iOS entry point. Swift cannot see default arguments, so every parameter is explicit and
 * everything is passed as a string or boolean (see [AppConfig.fromRaw]).
 * Swift sees this as `KoinIosKt.doInitKoin(...)`.
 *
 * [crashBridge] is the Swift Crashlytics bridge, or null when the build has no
 * `GoogleService-Info.plist`; crash reporting is then a no-op. [testCrashEnabled] is true only in
 * the Swift configurations that define `TEST_CRASH_ENABLED` (Debug and Stage, never Release).
 */
fun doInitKoin(
    environment: String,
    backendUrl: String?,
    demoAllowed: Boolean,
    versionName: String,
    versionCode: String,
    crashBridge: NativeCrashBridge?,
    testCrashEnabled: Boolean
): KoinApplication {
    val crashReporter = crashBridge?.let { IosCrashReporter(it) }
    val platformModule = module {
        if (crashReporter != null) {
            single<CrashReporter>(named(PLATFORM_CRASH_REPORTER)) { crashReporter }
        }
        if (testCrashEnabled) {
            single<TestCrashTrigger> { ThrowingTestCrashTrigger }
        }
    }
    val app = initKoin(
        config = AppConfig.fromRaw(
            environment = environment,
            backendUrl = backendUrl,
            demoAllowed = demoAllowed,
            versionName = versionName,
            versionCode = versionCode
        ),
        platformModules = listOf(platformModule)
    )
    if (crashReporter != null) installKotlinCrashHook(crashReporter, app.koin.get<CrashGate>())
    return app
}
