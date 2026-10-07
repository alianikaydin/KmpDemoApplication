package com.anksoft.myapplication.features.auth.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.logging.NoOpLogger
import com.anksoft.myapplication.core.storage.SessionManager
import com.anksoft.myapplication.features.auth.data.datasource.AuthRemoteDataSource
import com.anksoft.myapplication.features.auth.data.repository.AuthRepositoryImpl
import com.anksoft.myapplication.features.auth.domain.model.User
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test

class AuthRepositoryImplTest {

    private val successBody = """
        {
          "access_token": "access-123",
          "refresh_token": "refresh-456",
          "user": { "id": "u1", "email": "user@example.com", "name": "Test User" }
        }
    """.trimIndent()

    private fun repositoryReturning(
        status: HttpStatusCode,
        body: String
    ): Pair<AuthRepositoryImpl, SessionManager> {
        val engine = MockEngine {
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
            defaultRequest {
                url("https://test.local/")
                contentType(ContentType.Application.Json)
            }
        }
        val sessionManager = SessionManager(MapSettings())
        return AuthRepositoryImpl(AuthRemoteDataSource(client, NoOpLogger), sessionManager) to sessionManager
    }

    @Test
    fun successfulLoginReturnsUserAndPersistsSession() = runTest {
        val (repository, session) = repositoryReturning(HttpStatusCode.OK, successBody)

        val result = repository.login("user@example.com", "Password1")

        assertThat(result).isEqualTo(
            Result.Success(User(id = "u1", email = "user@example.com", name = "Test User"))
        )
        assertThat(session.getToken()).isEqualTo("access-123")
        assertThat(session.getRefreshToken()).isEqualTo("refresh-456")
        assertThat(session.getUserId()).isEqualTo("u1")
        assertThat(session.getUserEmail()).isEqualTo("user@example.com")
    }

    @Test
    fun unauthorizedMapsToUnauthorizedAndStoresNothing() = runTest {
        val (repository, session) = repositoryReturning(HttpStatusCode.Unauthorized, "{}")

        val result = repository.login("user@example.com", "wrong")

        assertThat(result).isEqualTo(Result.Failure(DataError.Remote.UNAUTHORIZED))
        assertThat(session.getToken()).isNull()
    }

    @Test
    fun conflictOnRegisterMapsToConflict() = runTest {
        val (repository, _) = repositoryReturning(HttpStatusCode.Conflict, "{}")

        val result = repository.register("taken@example.com", "Password1")

        assertThat(result).isEqualTo(Result.Failure(DataError.Remote.CONFLICT))
    }

    @Test
    fun tooManyRequestsMapsToTooManyRequests() = runTest {
        val (repository, _) = repositoryReturning(HttpStatusCode.TooManyRequests, "{}")

        assertThat(repository.login("user@example.com", "Password1"))
            .isEqualTo(Result.Failure(DataError.Remote.TOO_MANY_REQUESTS))
    }

    @Test
    fun serverErrorMapsToServerError() = runTest {
        val (repository, _) = repositoryReturning(HttpStatusCode.InternalServerError, "{}")

        assertThat(repository.login("user@example.com", "Password1"))
            .isEqualTo(Result.Failure(DataError.Remote.SERVER_ERROR))
    }

    @Test
    fun malformedBodyMapsToSerialization() = runTest {
        val (repository, session) = repositoryReturning(
            HttpStatusCode.OK,
            """{ "unexpected": true }"""
        )

        assertThat(repository.login("user@example.com", "Password1"))
            .isEqualTo(Result.Failure(DataError.Remote.SERIALIZATION))
        assertThat(session.getToken()).isNull()
    }

    @Test
    fun responseWithoutRefreshTokenStillPersistsAccessToken() = runTest {
        val (repository, session) = repositoryReturning(
            HttpStatusCode.OK,
            """
            {
              "access_token": "access-only",
              "user": { "id": "u1", "email": "user@example.com" }
            }
            """.trimIndent()
        )

        assertThat(repository.login("user@example.com", "Password1"))
            .isInstanceOf(Result.Success::class)
        assertThat(session.getToken()).isEqualTo("access-only")
        assertThat(session.getRefreshToken()).isNull()
    }

    @Test
    fun logoutClearsTheSession() = runTest {
        val (repository, session) = repositoryReturning(HttpStatusCode.OK, successBody)
        repository.login("user@example.com", "Password1")

        repository.logout()

        assertThat(session.getToken()).isNull()
        assertThat(session.getRefreshToken()).isNull()
        assertThat(session.getUserId()).isNull()
    }

    @Test
    fun getCurrentUserRehydratesFromStoredSession() = runTest {
        val (repository, _) = repositoryReturning(HttpStatusCode.OK, successBody)
        repository.login("user@example.com", "Password1")

        assertThat(repository.getCurrentUser())
            .isEqualTo(User(id = "u1", email = "user@example.com", name = "Test User"))
    }

    @Test
    fun getCurrentUserIsNullWithNoSession() = runTest {
        val (repository, _) = repositoryReturning(HttpStatusCode.OK, successBody)

        assertThat(repository.getCurrentUser()).isNull()
    }
}
