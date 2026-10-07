package com.anksoft.myapplication.core.logging

import com.anksoft.myapplication.core.config.AppConfig
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Qualifier of the platform console writer; tests override it to keep Logcat out of unit tests. */
const val CONSOLE_LOG_WRITER = "console"

/**
 * Binds the app logger. Every [LogWriter] registered in Koin (console by default, crash
 * reporting later) receives each entry that passes the environment's minimum severity.
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
