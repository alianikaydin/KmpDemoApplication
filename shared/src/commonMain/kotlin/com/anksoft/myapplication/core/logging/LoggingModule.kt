package com.anksoft.myapplication.core.logging

import com.anksoft.myapplication.core.config.AppConfig
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Qualifier of the platform console writer; tests override it to keep Logcat out of unit tests. */
const val CONSOLE_LOG_WRITER = "console"

/**
 * Binds the app logger. Every [LogWriter] registered in Koin (console by default, crash
 * reporting) receives each entry that passes its threshold: the environment's minimum severity,
 * or the writer's own `minSeverity` for a [RemoteLogWriter]. The writer list is captured once
 * when [AppLogger] is created, so writers must be registered in the modules passed to `initKoin`.
 */
val loggingModule = module {
    single<LogWriter>(named(CONSOLE_LOG_WRITER)) { KermitConsoleWriter() }
    single<AppLogger> {
        DispatchingLogger(
            minSeverity = get<AppConfig>().environment.minLogSeverity(),
            writers = getAll<LogWriter>()
        )
    }
}
