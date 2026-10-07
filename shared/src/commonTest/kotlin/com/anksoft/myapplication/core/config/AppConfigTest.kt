package com.anksoft.myapplication.core.config

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlin.test.assertFailsWith

class AppConfigTest {

    // AC-1
    @Test
    fun devWithBlankUrlAndDemoAllowedUsesMock() {
        assertThat(create(AppEnvironment.DEV, backendUrl = "  ", demoAllowed = true).useMockBackend).isTrue()
    }

    // AC-1
    @Test
    fun devUrlWithoutTrailingSlashGetsOne() {
        val config = create(AppEnvironment.DEV, backendUrl = " http://10.0.2.2:8081/api/v1 ", demoAllowed = false)

        assertThat(config.baseUrl).isEqualTo("http://10.0.2.2:8081/api/v1/")
    }

    // AC-1
    @Test
    fun devWithUrlUsesThatUrlWithoutMock() {
        val config = create(AppEnvironment.DEV, backendUrl = "http://10.0.2.2:8081/api/v1", demoAllowed = true)

        assertThat(config.environment).isEqualTo(AppEnvironment.DEV)
        assertThat(config.baseUrl).isEqualTo("http://10.0.2.2:8081/api/v1/")
        assertThat(config.useMockBackend).isFalse()
    }

    // AC-1
    @Test
    fun devWithoutUrlAndDemoAllowedUsesMock() {
        val config = create(AppEnvironment.DEV, backendUrl = null, demoAllowed = true)

        assertThat(config.baseUrl).isEqualTo(AppConfig.MOCK_BASE_URL)
        assertThat(config.useMockBackend).isTrue()
    }

    // AC-1
    @Test
    fun devWithoutUrlAndDemoNotAllowedFails() {
        assertFailsWith<IllegalStateException> {
            create(AppEnvironment.DEV, backendUrl = "", demoAllowed = false)
        }
    }

    // AC-1, AC-3
    @Test
    fun stageUsesFixedUrlAndIgnoresUrlAndDemo() {
        val config = create(AppEnvironment.STAGE, backendUrl = "http://localhost:8081/", demoAllowed = true)

        assertThat(config.baseUrl).isEqualTo(AppConfig.STAGE_BASE_URL)
        assertThat(config.useMockBackend).isFalse()
    }

    // AC-1, AC-3, AC-4
    @Test
    fun prodUsesFixedUrlAndIgnoresUrlAndDemo() {
        val config = create(AppEnvironment.PROD, backendUrl = "http://localhost:8081/", demoAllowed = true)

        assertThat(config.baseUrl).isEqualTo(AppConfig.PROD_BASE_URL)
        assertThat(config.useMockBackend).isFalse()
    }

    // AC-4
    @Test
    fun prodWithoutAnyInputDoesNotFallBackToMock() {
        val config = create(AppEnvironment.PROD, backendUrl = null, demoAllowed = false)

        assertThat(config.useMockBackend).isFalse()
    }

    // AC-5
    @Test
    fun stageAndProdUrlsDiffer() {
        assertThat(AppConfig.STAGE_BASE_URL == AppConfig.PROD_BASE_URL).isFalse()
    }

    // AC-3
    @Test
    fun prodConfigWithMockIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            AppConfig(
                environment = AppEnvironment.PROD,
                baseUrl = AppConfig.MOCK_BASE_URL,
                useMockBackend = true,
                versionName = "1.0",
                versionCode = 1
            )
        }
    }

    @Test
    fun createRejectsNonHttpUrlInDev() {
        assertFailsWith<IllegalArgumentException> {
            create(AppEnvironment.DEV, backendUrl = "ftp://example.com/", demoAllowed = true)
        }
    }

    // AC-10
    @Test
    fun createCarriesVersion() {
        val config = AppConfig.create(AppEnvironment.PROD, null, false, versionName = "2.3", versionCode = 17)

        assertThat(config.versionName).isEqualTo("2.3")
        assertThat(config.versionCode).isEqualTo(17)
    }

    // AC-10
    @Test
    fun invalidVersionIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            AppConfig.create(AppEnvironment.PROD, null, false, versionName = " ", versionCode = 1)
        }
        assertFailsWith<IllegalArgumentException> {
            AppConfig.create(AppEnvironment.PROD, null, false, versionName = "1.0", versionCode = 0)
        }
    }

    // AC-9, AC-10
    @Test
    fun fromRawParsesStringInputs() {
        val config = AppConfig.fromRaw("stage", null, false, "1.4", "12")

        assertThat(config.environment).isEqualTo(AppEnvironment.STAGE)
        assertThat(config.versionName).isEqualTo("1.4")
        assertThat(config.versionCode).isEqualTo(12)
    }

    // AC-9
    @Test
    fun fromRawRejectsInvalidVersionCode() {
        assertFailsWith<IllegalArgumentException> {
            AppConfig.fromRaw("prod", null, false, "1.0", "abc")
        }
    }

    // AC-9
    @Test
    fun fromRawRejectsUnknownEnvironment() {
        assertFailsWith<IllegalArgumentException> {
            AppConfig.fromRaw("qa", null, false, "1.0", "1")
        }
    }

    private fun create(environment: AppEnvironment, backendUrl: String?, demoAllowed: Boolean) =
        AppConfig.create(environment, backendUrl, demoAllowed, versionName = "1.0", versionCode = 1)
}
