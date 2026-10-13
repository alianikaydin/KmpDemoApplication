package com.anksoft.myapplication.core.crash

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.anksoft.kmpdemo.contract.auth.AuthResponseDto
import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.logging.DispatchingLogger
import com.anksoft.myapplication.core.logging.LogSeverity
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.error
import com.anksoft.myapplication.core.network.safeCall
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test

/** The real logger, writer and gate with a fake vendor: what a caught error looks like when it leaves. */
class CrashPipelineTest {

    private val reporter = FakeCrashReporter()
    private val gate = CrashGate().also { it.open() }

    // Prod-like: the console minimum is WARN, the crash writer still wants INFO.
    private val logger: AppLogger = DispatchingLogger(
        LogSeverity.WARN,
        listOf(CrashLogWriter(GatedCrashReporter(reporter, gate)))
    )

    private val path = "auth/login"

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest { url("https://test.local/") }
        }

    private fun allText(): List<String> =
        reporter.breadcrumbs + reporter.nonFatals.flatMap { listOf(it.typeName, it.message) + it.causeTypeNames }

    // AC-14, AC-16
    @Test
    fun loggedErrorReachesReporterWithRedactedMessageAndNoThrowableText() {
        val failure = IllegalStateException(
            "alice@example.com failed",
            RuntimeException("Bearer abc.def-123 password=hunter2")
        )

        logger.error(LogTags.APP, failure) { "Sync failed for bob@example.com" }

        val report = reporter.nonFatals.single()
        assertThat(report.message).isEqualTo("Sync failed for ***")
        assertThat(report.causeTypeNames).isEqualTo(listOf("RuntimeException"))
        allText().forEach { text ->
            assertThat(text).doesNotContain("alice@example.com")
            assertThat(text).doesNotContain("bob@example.com")
            assertThat(text).doesNotContain("abc.def-123")
            assertThat(text).doesNotContain("hunter2")
        }
    }

    // AC-15
    @Test
    fun safeCallUnknownFailureProducesNonFatalWithPathAndStatusOnly() = runTest {
        val http = client { throw IllegalStateException("connect to secret.example.com failed: {\"password\":\"hunter2\"}") }

        safeCall<AuthResponseDto>(logger, path) { http.post(path) }

        val report = reporter.nonFatals.single()
        assertThat(report.typeName).isEqualTo("IllegalStateException")
        assertThat(report.message).isEqualTo("Remote failure error=UNKNOWN path=$path status=-")
        allText().forEach { text ->
            assertThat(text).doesNotContain("secret.example.com")
            assertThat(text).doesNotContain("hunter2")
        }
    }

    // AC-15: a server error carries no throwable, so it is only a breadcrumb.
    @Test
    fun safeCallServerErrorIsOnlyABreadcrumb() = runTest {
        val http = client {
            respond(
                content = """{"email":"a@b.com","password":"hunter2"}""",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        safeCall<AuthResponseDto>(logger, path) { http.post(path) }

        assertThat(reporter.nonFatals).isEmpty()
        val breadcrumb = reporter.breadcrumbs.single()
        assertThat(breadcrumb).contains("SERVER_ERROR")
        assertThat(breadcrumb).contains(path)
        assertThat(breadcrumb).doesNotContain("hunter2")
        assertThat(breadcrumb).doesNotContain("a@b.com")
    }

    // AC-4
    @Test
    fun closedGateKeepsEveryLoggedEntryAwayFromTheVendor() {
        val closedReporter = FakeCrashReporter()
        val closedLogger = DispatchingLogger(
            LogSeverity.WARN,
            listOf(CrashLogWriter(GatedCrashReporter(closedReporter, CrashGate())))
        )

        closedLogger.error(LogTags.APP, IllegalStateException("x")) { "failed" }

        assertThat(closedReporter.calls).isEmpty()
    }
}
