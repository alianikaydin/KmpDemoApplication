package com.anksoft.myapplication.crash

import android.content.Context
import com.anksoft.myapplication.core.crash.CrashReporter
import com.anksoft.myapplication.core.crash.PLATFORM_CRASH_REPORTER
import com.anksoft.myapplication.core.crash.SanitizingExceptionHandler
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Binds the Firebase reporter when this build has a Firebase configuration; an empty module
 * otherwise. Call it from `Application.onCreate`: Firebase's init provider has already started
 * and installed Crashlytics' crash handler by then, so the sanitizing handler goes in front of it.
 */
fun androidCrashModule(context: Context): Module {
    val reporter = FirebaseCrashReporter.createOrNull(context) ?: return module { }
    SanitizingExceptionHandler.install()
    return module {
        single<CrashReporter>(named(PLATFORM_CRASH_REPORTER)) { reporter }
    }
}
