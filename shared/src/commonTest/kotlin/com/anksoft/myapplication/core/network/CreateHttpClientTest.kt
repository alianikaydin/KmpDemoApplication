package com.anksoft.myapplication.core.network

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isEmpty
import assertk.assertions.isFalse
import assertk.assertions.isNotEmpty
import com.anksoft.myapplication.core.config.AppConfig
import com.anksoft.myapplication.core.config.AppEnvironment
import com.anksoft.myapplication.core.config.TestAppConfigs
import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.logging.LogSeverity
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.NoOpLogger
import com.anksoft.myapplication.core.logging.recordingLogger
import com.anksoft.myapplication.core.storage.SessionManager
import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.headersOf
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
            sessionManager = SessionManager(MapSettings()),
            logger = NoOpLogger
        )

        client.get("auth/ping")

        assertThat(requestedUrl.toString()).isEqualTo("https://configured.test/api/auth/ping")
    }

    private fun clientFor(
        environment: AppEnvironment,
        logger: AppLogger,
        sessionManager: SessionManager = SessionManager(MapSettings()),
        engine: MockEngine = MockEngine { respondOk() }
    ) = createHttpClient(
        engine = engine,
        config = AppConfig.create(
            environment = environment,
            backendUrl = "https://configured.test/api/",
            demoAllowed = false,
            versionName = "1.0",
            versionCode = 1
        ),
        sessionManager = sessionManager,
        logger = logger
    )

    // AC-5
    @Test
    fun devLogsMaskTheBearerTokenTheSessionAttaches() = runTest {
        val (logger, writer) = recordingLogger()
        val sessionManager = SessionManager(MapSettings()).apply { saveToken("secret-token-value") }
        val client = clientFor(AppEnvironment.DEV, logger, sessionManager)

        client.get("auth/me") {
            header("X-Plain", "visible-value")
        }

        val log = writer.entries.joinToString("\n") { it.message }
        assertThat(log).doesNotContain("secret-token-value")
        // Ktor replaces a sanitized header value with "***".
        assertThat(log).contains("${HttpHeaders.Authorization}: ***")
        // Other headers stay readable, so the log is still useful.
        assertThat(log).contains("visible-value")
    }

    // AC-5
    @Test
    fun devHttpLogLinesUseTheHttpTagAtDebugSeverity() = runTest {
        val (logger, writer) = recordingLogger()

        clientFor(AppEnvironment.DEV, logger).get("auth/ping")

        assertThat(writer.entries).isNotEmpty()
        assertThat(writer.entries.map { it.tag to it.severity }.distinct())
            .isEqualTo(listOf(LogTags.HTTP to LogSeverity.DEBUG))
    }

    // AC-5
    @Test
    fun responseSetCookieValueIsMasked() = runTest {
        val (logger, writer) = recordingLogger()
        val engine = MockEngine {
            respond("", headers = headersOf(HttpHeaders.SetCookie, "sid=abc123; HttpOnly"))
        }

        clientFor(AppEnvironment.DEV, logger, engine = engine).get("auth/ping")

        val log = writer.entries.joinToString("\n") { it.message }
        assertThat(log).doesNotContain("abc123")
        assertThat(log).contains("${HttpHeaders.SetCookie}: ***")
    }

    // AC-5
    @Test
    fun lowerCaseSetCookieHeaderIsMasked() = runTest {
        val (logger, writer) = recordingLogger()
        val engine = MockEngine {
            respond("", headers = headersOf("set-cookie", "sid=abc123; HttpOnly"))
        }

        clientFor(AppEnvironment.DEV, logger, engine = engine).get("auth/ping")

        val log = writer.entries.joinToString("\n") { it.message }
        assertThat(log).doesNotContain("abc123")
        assertThat(log).contains("set-cookie: ***")
    }

    // AC-5
    @Test
    fun prodAndStageLogNoHttpTraffic() = runTest {
        listOf(AppEnvironment.PROD, AppEnvironment.STAGE).forEach { environment ->
            val (logger, writer) = recordingLogger()

            clientFor(environment, logger).get("auth/ping")

            assertThat(writer.entries.filter { it.tag == LogTags.HTTP }).isEmpty()
        }
    }

    // AC-5
    @Test
    fun httpLogLevelIsHeadersOnlyInDevAndNeverBodyOrAll() {
        assertThat(AppEnvironment.DEV.httpLogLevel()).isEqualTo(LogLevel.HEADERS)
        assertThat(AppEnvironment.STAGE.httpLogLevel()).isEqualTo(LogLevel.NONE)
        assertThat(AppEnvironment.PROD.httpLogLevel()).isEqualTo(LogLevel.NONE)
    }
}
