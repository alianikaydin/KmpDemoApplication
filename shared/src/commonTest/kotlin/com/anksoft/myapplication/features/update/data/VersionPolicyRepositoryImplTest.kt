package com.anksoft.myapplication.features.update.data

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.anksoft.myapplication.core.config.TestAppConfigs
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.logging.LogSeverity
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.NoOpLogger
import com.anksoft.myapplication.core.logging.recordingLogger
import com.anksoft.myapplication.core.network.createHttpClient
import com.anksoft.myapplication.core.storage.SessionManager
import com.anksoft.myapplication.features.update.data.datasource.VersionPolicyRemoteDataSource
import com.anksoft.myapplication.features.update.data.repository.VersionPolicyRepositoryImpl
import com.anksoft.myapplication.features.update.domain.model.AppVersion
import com.anksoft.myapplication.features.update.domain.model.UpdatePlatform
import com.anksoft.myapplication.features.update.domain.model.VersionPolicy
import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class VersionPolicyRepositoryImplTest {

    private class Recorded(
        val method: HttpMethod,
        val path: String,
        val query: Map<String, List<String>>,
        val authorization: String?,
        val body: String,
    )

    private val requests = mutableListOf<Recorded>()
    private var answerStatus = HttpStatusCode.OK
    private var answerBody = ""

    // The real client stack with a signed-in session: the bearer plugin must still keep the token away.
    private val session = SessionManager(MapSettings()).apply {
        saveToken("access-1")
        saveRefreshToken("refresh-1")
        saveUserId("u1")
    }
    private val client = createHttpClient(
        engine = MockEngine { request ->
            requests += Recorded(
                method = request.method,
                path = request.url.encodedPath,
                query = request.url.parameters.entries().associate { it.key to it.value },
                authorization = request.headers[HttpHeaders.Authorization],
                body = request.body.toByteArray().decodeToString(),
            )
            respond(
                content = answerBody,
                status = answerStatus,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        },
        config = TestAppConfigs.remote("https://api.test/api/v1/"),
        sessionManager = session,
        logger = NoOpLogger,
    )
    private val recorded = recordingLogger()
    private val logger = recorded.first
    private val log = recorded.second
    private val repository = VersionPolicyRepositoryImpl(VersionPolicyRemoteDataSource(client, logger), logger)

    @AfterTest
    fun tearDown() {
        client.close()
    }

    // AC-18, AC-21
    @Test
    fun theRequestIsAGetOnTheRelativePathWithOnlyThePlatformAndNoBodyOrToken() = runTest {
        answerBody = """{"min_supported_version":"1.0.0","latest_version":"1.2.0"}"""

        repository.getVersionPolicy(UpdatePlatform.ANDROID)

        val request = requests.single()
        assertThat(request.method).isEqualTo(HttpMethod.Get)
        assertThat(request.path).isEqualTo("/api/v1/app/version-policy")
        assertThat(request.query).isEqualTo(mapOf("platform" to listOf("android")))
        assertThat(request.body).isEqualTo("")
        assertThat(request.authorization).isNull()
    }

    // AC-18
    @Test
    fun theIosPlatformIsSentAsIos() = runTest {
        answerBody = """{"min_supported_version":"1.0.0","latest_version":"1.2.0"}"""

        repository.getVersionPolicy(UpdatePlatform.IOS)

        assertThat(requests.single().query).isEqualTo(mapOf("platform" to listOf("ios")))
    }

    // AC-15
    @Test
    fun aValidAnswerBecomesAPolicy() = runTest {
        answerBody = """{"min_supported_version":"1.0.0","latest_version":"1.2.0"}"""

        val result = repository.getVersionPolicy(UpdatePlatform.ANDROID)

        assertThat(result).isEqualTo(Result.Success(VersionPolicy(AppVersion(1, 0, 0), AppVersion(1, 2, 0))))
        assertThat(log.entries).isEmpty()
    }

    // AC-15
    @Test
    fun anAnswerWithAMissingFieldIsASerializationFailure() = runTest {
        answerBody = """{"latest_version":"1.2.0"}"""

        val result = repository.getVersionPolicy(UpdatePlatform.ANDROID)

        assertThat(result).isEqualTo(Result.Failure(DataError.Remote.SERIALIZATION))
    }

    // AC-15
    @Test
    fun anAnswerWithAnUnreadableVersionIsAWarningWithoutTheValue() = runTest {
        answerBody = """{"min_supported_version":"banana-7","latest_version":"1.2.0"}"""

        val result = repository.getVersionPolicy(UpdatePlatform.ANDROID)

        assertThat(result).isEqualTo(Result.Failure(DataError.Remote.SERIALIZATION))
        val entry = log.entries.single()
        assertThat(entry.severity).isEqualTo(LogSeverity.WARN)
        assertThat(entry.tag).isEqualTo(LogTags.UPDATE)
        assertThat(entry.message).doesNotContain("banana")
        assertThat(entry.message).doesNotContain("1.2.0")
    }

    // AC-14
    @Test
    fun aServerErrorIsLoggedWithPathAndStatusButNoBody() = runTest {
        answerStatus = HttpStatusCode.InternalServerError
        answerBody = """{"secret":"do-not-log"}"""

        val result = repository.getVersionPolicy(UpdatePlatform.ANDROID)

        assertThat(result).isEqualTo(Result.Failure(DataError.Remote.SERVER_ERROR))
        val message = log.entries.single().message
        assertThat(message).contains("app/version-policy")
        assertThat(message).contains("status=500")
        assertThat(message).doesNotContain("do-not-log")
    }

    // AC-6, AC-14, AC-21
    @Test
    fun anUnauthorizedAnswerIsAFailureWithoutARefreshAndTheSessionStays() = runTest {
        answerStatus = HttpStatusCode.Unauthorized

        val result = repository.getVersionPolicy(UpdatePlatform.ANDROID)

        assertThat(result).isEqualTo(Result.Failure(DataError.Remote.UNAUTHORIZED))
        assertThat(requests.map { it.path }).containsExactly("/api/v1/app/version-policy")
        assertThat(session.getToken()).isEqualTo("access-1")
        assertThat(session.getRefreshToken()).isEqualTo("refresh-1")
    }
}
