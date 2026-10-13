package com.anksoft.myapplication.core.crash

/**
 * What leaves the device for a caught error. [stackSource] is only used for its stack frames;
 * its message and its causes' messages are never read.
 *
 * Not a data class on purpose: [stackSource] must not take part in equality.
 *
 * @property typeName Simple class name of the error.
 * @property message Text we wrote (a log message), never the throwable's own message.
 * @property causeTypeNames Simple class names of the cause chain, nearest cause first.
 */
class NonFatalReport(
    val typeName: String,
    val message: String,
    val causeTypeNames: List<String>,
    val stackSource: Throwable,
) {
    fun withMessage(message: String): NonFatalReport =
        NonFatalReport(typeName, message, causeTypeNames, stackSource)

    companion object {
        private const val MAX_CAUSE_DEPTH = 5

        /** [message] must already be text we wrote (a log message), never `throwable.message`. */
        fun from(throwable: Throwable, message: String): NonFatalReport {
            val seen = mutableListOf(throwable)
            val causeTypes = mutableListOf<String>()
            var next = throwable.cause
            while (causeTypes.size < MAX_CAUSE_DEPTH) {
                val cause = next ?: break
                // A cause chain that points back to itself must not loop forever.
                if (seen.any { it === cause }) break
                seen += cause
                causeTypes += cause.typeName()
                next = cause.cause
            }
            return NonFatalReport(throwable.typeName(), message, causeTypes, throwable)
        }

        // Same rule as DispatchingLogger: the simple class name, never the message.
        private fun Throwable.typeName(): String = this::class.simpleName ?: "Throwable"
    }
}
