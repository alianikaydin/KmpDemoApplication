package com.anksoft.myapplication.core.network.mock

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import com.anksoft.kmpdemo.contract.auth.AuthResponseDto
import com.anksoft.myapplication.core.config.AppConfig
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.network.createHttpClient
import com.anksoft.myapplication.core.network.safeCall
import com.anksoft.myapplication.core.storage.SessionManager
import com.anksoft.myapplication.features.auth.data.datasource.AuthRemoteDataSource
import com.anksoft.myapplication.features.auth.data.repository.AuthRepositoryImpl
import com.anksoft.myapplication.features.auth.domain.model.User
import com.russhwolf.settings.MapSettings
import io.ktor.client.request.post
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class MockAuthServerTest {

    private val sessionManager = SessionManager(MapSettings())
    private val httpClient = createHttpClient(
        engine = createMockEngine(MockAuthServer()),
        config = AppConfig.Demo,
        sessionManager = sessionManager
    )
    private val remoteDataSource = AuthRemoteDataSource(httpClient)
    private val repository = AuthRepositoryImpl(remoteDataSource, sessionManager)

    private val demoUser = MockAuthServer.DEMO_USER

    @Test
    fun demoUserLoginSucceedsAndPersistsSession() = runTest {
        val result = repository.login(demoUser.email, demoUser.password)

        assertThat(result).isEqualTo(
            Result.Success(User(id = "demo-1", email = demoUser.email, name = demoUser.name))
        )
        assertThat(sessionManager.getToken()).isNotNull()
        assertThat(sessionManager.getRefreshToken()).isNotNull()
    }

    @Test
    fun wrongPasswordReturnsUnauthorized() = runTest {
        val result = repository.login(demoUser.email, "Wrong1234")

        assertThat(result).isEqualTo(Result.Failure(DataError.Remote.UNAUTHORIZED))
    }

    @Test
    fun unknownEmailReturnsUnauthorized() = runTest {
        val result = repository.login("nobody@example.com", demoUser.password)

        assertThat(result).isEqualTo(Result.Failure(DataError.Remote.UNAUTHORIZED))
    }

    @Test
    fun loginIgnoresEmailCase() = runTest {
        val result = repository.login(demoUser.email.uppercase(), demoUser.password)

        assertThat(result).isInstanceOf(Result.Success::class)
    }

    @Test
    fun registeredUserCanLogIn() = runTest {
        val register = repository.register("new@example.com", "Secret123")
        val login = repository.login("new@example.com", "Secret123")

        assertThat(register).isInstanceOf(Result.Success::class)
        assertThat(login).isInstanceOf(Result.Success::class)
    }

    @Test
    fun registeringExistingEmailReturnsConflict() = runTest {
        val result = repository.register(demoUser.email, "Another123")

        assertThat(result).isEqualTo(Result.Failure(DataError.Remote.CONFLICT))
    }

    @Test
    fun loginResponseMatchesAuthContract() = runTest {
        val result = remoteDataSource.login(demoUser.email, demoUser.password)

        val response = (result as Result.Success<AuthResponseDto>).data
        assertThat(response.accessToken).isNotNull()
        assertThat(response.refreshToken).isNotNull()
        assertThat(response.user.name).isEqualTo(demoUser.name)
    }

    @Test
    fun unknownPathReturnsUnknownError() = runTest {
        val result = safeCall<AuthResponseDto> { httpClient.post("auth/unknown") }

        assertThat(result).isEqualTo(Result.Failure(DataError.Remote.UNKNOWN))
    }
}
