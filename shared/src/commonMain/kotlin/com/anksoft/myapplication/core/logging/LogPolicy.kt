package com.anksoft.myapplication.core.logging

import com.anksoft.myapplication.core.config.AppEnvironment

/** Minimum severity written in each environment: DEV everything, STAGE info+, PROD warn+. */
fun AppEnvironment.minLogSeverity(): LogSeverity = when (this) {
    AppEnvironment.DEV -> LogSeverity.DEBUG
    AppEnvironment.STAGE -> LogSeverity.INFO
    AppEnvironment.PROD -> LogSeverity.WARN
}
