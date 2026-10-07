package com.anksoft.myapplication.core.network

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import com.anksoft.myapplication.core.config.AppConfig
import com.anksoft.myapplication.core.config.AppEnvironment
import com.anksoft.myapplication.core.config.TestAppConfigs
import com.anksoft.myapplication.core.storage.SessionManager
import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class CreateHttpClientTest {

    @Test
    fun prodConfigHasMockBackendDisabledAndUsesProdUrl() {
        // AC-3
        val config = AppConfig.create(AppEnvironment.PROD, null, false, versionName = "1.0", versionCode = 1)

        assertThat(config.useMockBackend).isFalse()
        assertThat(config.baseUrl).isEqualTo(AppConfig.PROD_BASE_URL)
    }

    @Test
    fun requestsUseBaseUrlFromConfig() = runTest {
        var requestedUrl: Url? = null
        val engine = MockEngine { request ->
            requestedUrl = request.url
            respondOk()
        }
        val client = createHttpClient(
            engine = engine,
            config = TestAppConfigs.remote("https://configured.test/api/"),
            sessionManager = SessionManager(MapSettings())
        )

        client.get("auth/ping")

        assertThat(requestedUrl.toString()).isEqualTo("https://configured.test/api/auth/ping")
    }

    @Test
    fun logsMaskTheBearerTokenTheSessionAttaches() = runTest {
        val lines = mutableListOf<String>()
        val sessionManager = SessionManager(MapSettings()).apply {
            saveToken("secret-token-value")
        }
        val client = createHttpClient(
            engine = MockEngine { respondOk() },
            config = TestAppConfigs.remote("https://configured.test/api/"),
            sessionManager = sessionManager,
            logger = object : Logger {
                override fun log(message: String) {
                    lines += message
                }
            }
        )

        client.get("auth/me") {
            header("X-Plain", "visible-value")
        }

        val log = lines.joinToString("\n")
        assertThat(log).doesNotContain("secret-token-value")
        // Ktor replaces a sanitized header value with "***".
        assertThat(log).contains("${HttpHeaders.Authorization}: ***")
        // Other headers stay readable, so the log is still useful.
        assertThat(log).contains("visible-value")
    }
}
