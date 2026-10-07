package com.anksoft.myapplication.core.logging

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.anksoft.myapplication.core.config.AppEnvironment
import kotlin.test.Test

class LogPolicyTest {

    // AC-2
    @Test
    fun devWritesEverything() {
        assertThat(AppEnvironment.DEV.minLogSeverity()).isEqualTo(LogSeverity.DEBUG)
    }

    // AC-3
    @Test
    fun stageDefaultsToInfo() {
        assertThat(AppEnvironment.STAGE.minLogSeverity()).isEqualTo(LogSeverity.INFO)
    }

    // AC-3
    @Test
    fun prodWritesOnlyWarnAndAbove() {
        assertThat(AppEnvironment.PROD.minLogSeverity()).isEqualTo(LogSeverity.WARN)
    }
}
