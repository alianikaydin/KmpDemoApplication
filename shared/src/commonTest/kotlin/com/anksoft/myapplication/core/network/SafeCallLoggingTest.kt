package com.anksoft.myapplication.core.network

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.anksoft.kmpdemo.contract.auth.AuthResponseDto
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.logging.LogSeverity
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.recordingLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test

class SafeCallLoggingTest {

    private val path = "auth/login"

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest { url("https://test.local/") }
        }

    private fun MockRequestHandleScope.respondJson(status: HttpStatusCode, body: String) = respond(
        content = body,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    )

    // AC-6
    @Test
    fun serverErrorIsLoggedOnceWithErrorTypePathAndStatusButNoBody() = runTest {
        val (logger, writer) = recordingLogger()
        val http = client { respondJson(HttpStatusCode.InternalServerError, """{"email":"a@b.com","password":"hunter2"}""") }

        val result = safeCall<AuthResponseDto>(logger, path) { http.post(path) }

        assertThat(result).isEqualTo(Result.Failure(DataError.Remote.SERVER_ERROR))
        val entry = writer.entries.single()
        assertThat(entry.tag).isEqualTo(LogTags.NETWORK)
        assertThat(entry.severity).isEqualTo(LogSeverity.ERROR)
        assertThat(entry.message).contains("SERVER_ERROR")
        assertThat(entry.message).contains(path)
        assertThat(entry.message).contains("500")
        assertThat(entry.message).doesNotContain("hunter2")
        assertThat(entry.message).doesNotContain("a@b.com")
    }

    // AC-6
    @Test
    fun queryStringOfTheRequestUrlIsNeverLogged() = runTest {
        val (logger, writer) = recordingLogger()
        val http = client { respondJson(HttpStatusCode.InternalServerError, "{}") }

        safeCall<AuthResponseDto>(logger, path) { http.get("$path?token=s3cretq") }

        assertThat(writer.entries.single().message).doesNotContain("s3cretq")
    }

    // AC-6
    @Test
    fun unauthorizedIsLoggedAsWarning() = runTest {
        val (logger, writer) = recordingLogger()
        val http = client { respondJson(HttpStatusCode.Unauthorized, "{}") }

        safeCall<AuthResponseDto>(logger, path) { http.post(path) }

        val entry = writer.entries.single()
        assertThat(entry.severity).isEqualTo(LogSeverity.WARN)
        assertThat(entry.message).contains("UNAUTHORIZED")
    }

    // AC-6
    @Test
    fun unexpectedExceptionLogsClassNameButNotItsMessage() = runTest {
        val (logger, writer) = recordingLogger()
        val http = client { throw IllegalStateException("leaked secret value") }

        val result = safeCall<AuthResponseDto>(logger, path) { http.post(path) }

        assertThat(result).isEqualTo(Result.Failure(DataError.Remote.UNKNOWN))
        val entry = writer.entries.single()
        assertThat(entry.severity).isEqualTo(LogSeverity.ERROR)
        assertThat(entry.throwableName).isEqualTo("IllegalStateException")
        assertThat(entry.message).doesNotContain("leaked secret value")
    }

    // AC-6
    @Test
    fun unresolvedAddressIsLoggedAsNoInternetWarning() = runTest {
        val (logger, writer) = recordingLogger()
        val http = client { throw UnresolvedAddressException() }

        val result = safeCall<AuthResponseDto>(logger, path) { http.post(path) }

        assertThat(result).isEqualTo(Result.Failure(DataError.Remote.NO_INTERNET))
        val entry = writer.entries.single()
        assertThat(entry.severity).isEqualTo(LogSeverity.WARN)
        assertThat(entry.message).contains("NO_INTERNET")
    }

    // AC-6
    @Test
    fun malformedSuccessBodyIsLoggedAsSerializationError() = runTest {
        val (logger, writer) = recordingLogger()
        val http = client { respondJson(HttpStatusCode.OK, """{ "unexpected": true }""") }

        val result = safeCall<AuthResponseDto>(logger, path) { http.post(path) }

        assertThat(result).isEqualTo(Result.Failure(DataError.Remote.SERIALIZATION))
        assertThat(writer.entries.single().message).contains("SERIALIZATION")
    }

    // AC-6
    @Test
    fun successLogsNothing() = runTest {
        val (logger, writer) = recordingLogger()
        val http = client {
            respondJson(
                HttpStatusCode.OK,
                """{"access_token":"a","refresh_token":"r","user":{"id":"u1","email":"u@e.com"}}"""
            )
        }

        val result = safeCall<AuthResponseDto>(logger, path) { http.post(path) }

        assertThat(result).isInstanceOf(Result.Success::class)
        assertThat(writer.entries).isEmpty()
    }

    // AC-6
    @Test
    fun cancellationIsNotLoggedAndStillPropagates() = runTest {
        val (logger, writer) = recordingLogger()
        val started = CompletableDeferred<Unit>()
        val http = client {
            started.complete(Unit)
            awaitCancellation()
        }

        val job = launch { safeCall<AuthResponseDto>(logger, path) { http.post(path) } }
        started.await()
        job.cancel()
        job.join()

        assertThat(job.isCancelled).isTrue()
        assertThat(writer.entries).isEmpty()
    }

    // AC-6
    @Test
    fun logSeverityTreatsRecoverableFailuresAsWarnings() {
        val warnings = DataError.Remote.entries.filter { it.logSeverity() == LogSeverity.WARN }

        assertThat(warnings).isEqualTo(
            listOf(
                DataError.Remote.NO_INTERNET,
                DataError.Remote.REQUEST_TIMEOUT,
                DataError.Remote.TOO_MANY_REQUESTS,
                DataError.Remote.UNAUTHORIZED,
                DataError.Remote.CONFLICT
            )
        )
    }
}
