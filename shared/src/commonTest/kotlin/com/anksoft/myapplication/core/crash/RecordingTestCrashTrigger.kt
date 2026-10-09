package com.anksoft.myapplication.core.crash

/** Counts the calls, then throws the real test crash exception. */
class RecordingTestCrashTrigger : TestCrashTrigger {
    var callCount = 0
        private set

    override fun crash(): Nothing {
        callCount++
        throw TestCrashException()
    }
}
