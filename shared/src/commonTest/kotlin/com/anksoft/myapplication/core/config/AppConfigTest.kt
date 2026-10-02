package com.anksoft.myapplication.core.config

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import kotlin.test.Test
import kotlin.test.assertFailsWith

class AppConfigTest {

    // AC-15
    @Test
    fun blank_or_null_url_returns_demo() {
        assertThat(AppConfig.fromBackendUrl(null)).isEqualTo(AppConfig.Demo)
        assertThat(AppConfig.fromBackendUrl("")).isEqualTo(AppConfig.Demo)
        assertThat(AppConfig.fromBackendUrl("  ")).isEqualTo(AppConfig.Demo)
    }

    // AC-14
    @Test
    fun url_without_trailing_slash_gets_one() {
        val config = AppConfig.fromBackendUrl(" http://10.0.2.2:8081/api/v1 ")

        assertThat(config.baseUrl).isEqualTo("http://10.0.2.2:8081/api/v1/")
    }

    // AC-14
    @Test
    fun real_url_disables_mock() {
        val config = AppConfig.fromBackendUrl("https://localhost:8081/api/v1/")

        assertThat(config.useMockBackend).isFalse()
        assertThat(config.baseUrl).isEqualTo("https://localhost:8081/api/v1/")
    }

    @Test
    fun non_http_scheme_throws() {
        assertFailsWith<IllegalArgumentException> {
            AppConfig.fromBackendUrl("ftp://example.com/")
        }
    }

    // AC-15
    @Test
    fun release_ignores_url() {
        assertThat(AppConfig.forBuild(isDebug = false, backendUrl = "http://localhost:8081/api/v1/"))
            .isEqualTo(AppConfig())
    }

    // AC-14
    @Test
    fun debug_uses_url() {
        val config = AppConfig.forBuild(isDebug = true, backendUrl = "http://localhost:8081/api/v1/")

        assertThat(config.useMockBackend).isFalse()
    }
}
