package com.anksoft.myapplication.core.crash

/** Bound only in dev and stage builds by the platform. No binding means no test crash action. */
fun interface TestCrashTrigger {
    fun crash(): Nothing
}

/** The controlled exception of the test crash. Its message never reaches a report; the type name does. */
class TestCrashException : RuntimeException("Test crash requested from Settings")

/** The single implementation; platforms decide whether to bind it. */
object ThrowingTestCrashTrigger : TestCrashTrigger {
    override fun crash(): Nothing = throw TestCrashException()
}
