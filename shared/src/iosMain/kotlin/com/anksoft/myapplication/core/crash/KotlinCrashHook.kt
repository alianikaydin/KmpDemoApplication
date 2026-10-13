package com.anksoft.myapplication.core.crash

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.setUnhandledExceptionHook
import kotlin.native.terminateWithUnhandledException

/**
 * Reports an uncaught Kotlin exception (with its Kotlin stack) while the gate is open, then ends
 * the process the way Kotlin/Native does by default. The hook never throws: a failing report must
 * not hide the original crash.
 */
@OptIn(ExperimentalNativeApi::class)
internal fun installKotlinCrashHook(reporter: IosCrashReporter, gate: CrashGate) {
    setUnhandledExceptionHook { throwable ->
        if (gate.isOpen) reporter.recordUncaught(throwable)
        terminateWithUnhandledException(throwable)
    }
}
