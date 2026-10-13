package com.anksoft.myapplication.core.crash

import com.anksoft.myapplication.core.config.AppConfig
import com.anksoft.myapplication.core.di.ApplicationScope
import com.anksoft.myapplication.core.di.CrashReportingSettings
import com.anksoft.myapplication.core.logging.LogWriter
import com.russhwolf.settings.Settings
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Qualifier under which a platform binds its [CrashReporter] (in the `platformModules` given to
 * `initKoin`). Nothing bound means no vendor: the controller stays idle and everything is a no-op.
 */
const val PLATFORM_CRASH_REPORTER = "platformCrashReporter"

/** Qualifier of the crash [LogWriter]; the logger collects it with the other writers. */
const val CRASH_LOG_WRITER = "crash"

val crashModule = module {
    single { CrashGate() }

    // The settings are resolved on first use, which only happens once consent is granted.
    single { InstallIdStore(settingsProvider = { get<Settings>(CrashReportingSettings) }) }

    single<LogWriter>(named(CRASH_LOG_WRITER)) {
        val vendor = getOrNull<CrashReporter>(named(PLATFORM_CRASH_REPORTER)) ?: NoOpCrashReporter
        CrashLogWriter(GatedCrashReporter(vendor, get()))
    }

    single {
        CrashReportingController(
            reporter = getOrNull<CrashReporter>(named(PLATFORM_CRASH_REPORTER)),
            gate = get(),
            consent = get(),
            installIds = get(),
            environment = get<AppConfig>().environment,
            logger = get(),
            scope = get(ApplicationScope),
        )
    }
}
