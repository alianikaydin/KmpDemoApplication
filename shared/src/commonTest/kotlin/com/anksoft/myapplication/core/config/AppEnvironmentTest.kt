package com.anksoft.myapplication.core.config

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.test.assertFailsWith

class AppEnvironmentTest {

    // AC-1
    @Test
    fun parseIsCaseInsensitive() {
        assertThat(AppEnvironment.parse("prod")).isEqualTo(AppEnvironment.PROD)
        assertThat(AppEnvironment.parse("STAGE")).isEqualTo(AppEnvironment.STAGE)
        assertThat(AppEnvironment.parse(" Dev ")).isEqualTo(AppEnvironment.DEV)
    }

    // AC-1
    @Test
    fun parseRejectsBlankValue() {
        assertFailsWith<IllegalArgumentException> { AppEnvironment.parse("") }
        assertFailsWith<IllegalArgumentException> { AppEnvironment.parse("  ") }
    }

    // AC-1
    @Test
    fun parseRejectsUnknownValue() {
        assertFailsWith<IllegalArgumentException> { AppEnvironment.parse("qa") }
    }
}
