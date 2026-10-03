package com.anksoft.myapplication.core.network

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.anksoft.myapplication.core.config.AppConfig
import com.anksoft.myapplication.core.storage.SessionManager
import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class CreateHttpClientTest {

    @Test
    fun defaultConfigHasMockBackendDisabled() {
        assertThat(AppConfig().useMockBackend).isFalse()
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
            config = AppConfig(baseUrl = "https://configured.test/api/"),
            sessionManager = SessionManager(MapSettings())
        )

        client.get("auth/ping")

        assertThat(requestedUrl.toString()).isEqualTo("https://configured.test/api/auth/ping")
    }

    @Test
    fun logsNeverContainTheAuthorizationHeaderValue() = runTest {
        val lines = mutableListOf<String>()
        val client = createHttpClient(
            engine = MockEngine { respondOk() },
            config = AppConfig(baseUrl = "https://configured.test/api/"),
            sessionManager = SessionManager(MapSettings()),
            logger = object : Logger {
                override fun log(message: String) {
                    lines += message
                }
            }
        )

        client.get("auth/me") {
            header(HttpHeaders.Authorization, "Bearer secret-token-value")
            header("X-Plain", "visible-value")
        }

        val log = lines.joinToString("\n")
        assertThat(log).doesNotContain("secret-token-value")
        assertThat(log.contains(HttpHeaders.Authorization)).isTrue()
        // Other headers stay readable, so the log is still useful.
        assertThat(log).contains("visible-value")
    }
}
