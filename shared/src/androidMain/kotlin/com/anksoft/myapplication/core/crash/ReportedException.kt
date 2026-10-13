package com.anksoft.myapplication.core.crash

/**
 * The only throwable shape that is handed to the Android crash vendor. It carries a label we
 * wrote (or just a type name), the stack frames of the original error and, for its causes, only
 * their type names and stack frames. No message of the original error or of its causes is ever
 * copied, because such messages can hold URLs, hosts or response text.
 *
 * Pure JVM code on purpose, so it is unit tested on the host without Firebase.
 */
class ReportedException private constructor(
    label: String,
    cause: Throwable?,
) : RuntimeException(label, cause) {

    companion object {
        private const val MAX_CAUSE_DEPTH = 5

        /** Non-fatal report: label is `"<Type>: <our log message>"`, causes keep type and stack only. */
        fun from(report: NonFatalReport): ReportedException {
            val causes = causeChain(report.stackSource)
            val labelled = report.causeTypeNames.zip(causes)
            return rebuild("${report.typeName}: ${report.message}", report.stackSource, labelled)
        }

        /** Fatal path: label is only the type name, so even a log message cannot leak. */
        fun sanitize(throwable: Throwable): ReportedException {
            val labelled = causeChain(throwable).map { it.typeName() to it }
            return rebuild(throwable.typeName(), throwable, labelled)
        }

        private fun rebuild(
            label: String,
            source: Throwable,
            causes: List<Pair<String, Throwable>>,
        ): ReportedException {
            // Built from the deepest cause outwards.
            var inner: ReportedException? = null
            for ((name, cause) in causes.asReversed()) {
                inner = copy(name, cause, inner)
            }
            return copy(label, source, inner)
        }

        private fun copy(label: String, source: Throwable, cause: Throwable?): ReportedException {
            val copy = ReportedException(label, cause)
            copy.stackTrace = source.stackTrace
            return copy
        }

        private fun causeChain(source: Throwable): List<Throwable> {
            val seen = mutableListOf(source)
            var next = source.cause
            while (seen.size <= MAX_CAUSE_DEPTH) {
                val cause = next ?: break
                if (seen.any { it === cause }) break
                seen += cause
                next = cause.cause
            }
            return seen.drop(1)
        }

        private fun Throwable.typeName(): String = this::class.simpleName ?: "Throwable"
    }
}
