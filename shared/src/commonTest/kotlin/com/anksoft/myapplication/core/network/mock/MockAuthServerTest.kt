package com.anksoft.myapplication.core.network.mock

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import com.anksoft.kmpdemo.contract.auth.AuthPaths
import com.anksoft.kmpdemo.contract.auth.AuthResponseDto
import com.anksoft.kmpdemo.contract.auth.LoginRequestDto
import com.anksoft.kmpdemo.contract.auth.RefreshTokenRequestDto
import com.anksoft.myapplication.core.config.TestAppConfigs
import com.anksoft.myapplication.core.domain.DataError
import com.anksoft.myapplication.core.domain.Result
import com.anksoft.myapplication.core.logging.NoOpLogger
import com.anksoft.myapplication.core.network.KtorAuthTokenCache
import com.anksoft.myapplication.core.network.createHttpClient
import com.anksoft.myapplication.core.network.safeCall
import com.anksoft.myapplication.core.storage.SessionManager
import com.anksoft.myapplication.features.auth.data.datasource.AuthRemoteDataSource
import com.anksoft.myapplication.features.auth.data.repository.AuthRepositoryImpl
import com.anksoft.myapplication.features.auth.domain.model.User
import com.anksoft.myapplication.features.consent.data.datasource.ConsentLocalDataSource
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class MockAuthServerTest {

    private val sessionManager = SessionManager(MapSettings())
    private val httpClient = createHttpClient(
        engine = createMockEngine(MockAuthServer()),
        config = TestAppConfigs.demo(),
        sessionManager = sessionManager,
        logger = NoOpLogger
    )
    private val remoteDataSource = AuthRemoteDataSource(httpClient, NoOpLogger)
    private val repository = AuthRepositoryImpl(
        remoteDataSource,
        sessionManager,
        KtorAuthTokenCache(httpClient),
        ConsentLocalDataSource(sessionManager),
        emptyList(),
        NoOpLogger
    )

    private val demoUser = MockAuthServer.DEMO_USER

    @AfterTest
    fun tearDown() {
        httpClient.close()
    }

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
        val register = repository.register("new@example.com", "Secret123", consent = null)
        val login = repository.login("new@example.com", "Secret123")

        assertThat(register).isInstanceOf(Result.Success::class)
        assertThat(login).isInstanceOf(Result.Success::class)
    }

    @Test
    fun registeringExistingEmailReturnsConflict() = runTest {
        val result = repository.register(demoUser.email, "Another123", consent = null)

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
        val result = safeCall<AuthResponseDto>(NoOpLogger, "auth/unknown") { httpClient.post("auth/unknown") }

        assertThat(result).isEqualTo(Result.Failure(DataError.Remote.UNKNOWN))
    }

    private suspend fun refresh(refreshToken: String, client: HttpClient = httpClient): HttpResponse =
        client.post(AuthPaths.REFRESH) { setBody(RefreshTokenRequestDto(refreshToken)) }

    private suspend fun demoLogin(): AuthResponseDto =
        (remoteDataSource.login(demoUser.email, demoUser.password) as Result.Success<AuthResponseDto>).data

    // AC-28
    @Test
    fun refreshIssuesANewTokenPairForTheSameUser() = runTest {
        val login = demoLogin()

        val response = refresh(requireNotNull(login.refreshToken))

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        val refreshed = response.body<AuthResponseDto>()
        assertThat(refreshed.user).isEqualTo(login.user)
        assertThat(refreshed.accessToken).isNotEqualTo(login.accessToken)
        assertThat(refreshed.refreshToken).isNotEqualTo(login.refreshToken)
    }

    // AC-28
    @Test
    fun aRefreshTokenWorksOnlyOnce() = runTest {
        val login = demoLogin()
        refresh(requireNotNull(login.refreshToken))

        val reuse = refresh(requireNotNull(login.refreshToken))

        assertThat(reuse.status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(reuse.headers[HttpHeaders.WWWAuthenticate]).isEqualTo("Bearer")
    }

    // AC-28
    @Test
    fun theRotatedRefreshTokenCanBeRefreshedAgain() = runTest {
        val first = refresh(requireNotNull(demoLogin().refreshToken)).body<AuthResponseDto>()

        val second = refresh(requireNotNull(first.refreshToken))

        assertThat(second.status).isEqualTo(HttpStatusCode.OK)
    }

    // AC-28
    @Test
    fun unknownRefreshTokensAreRejected() = runTest {
        listOf("garbage", "mock-refresh-", "mock-refresh-demo-99-1", "mock-refresh-nodash").forEach { token ->
            val response = refresh(token)

            assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
            assertThat(response.headers[HttpHeaders.WWWAuthenticate]).isEqualTo("Bearer")
        }
    }

    // AC-28
    @Test
    fun refreshTokenFromBeforeAProcessRestartStillWorksForSeededUsers() = runTest {
        val login = demoLogin()
        val restartedClient = createHttpClient(
            engine = createMockEngine(MockAuthServer()),
            config = TestAppConfigs.demo(),
            sessionManager = SessionManager(MapSettings()),
            logger = NoOpLogger
        )

        try {
            val response = refresh(requireNotNull(login.refreshToken), restartedClient)

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        } finally {
            restartedClient.close()
        }
    }

    // AC-28
    @Test
    fun everyUnauthorizedAnswerAsksForBearerAuth() = runTest {
        val response = httpClient.post(AuthPaths.LOGIN) {
            setBody(LoginRequestDto(demoUser.email, "Wrong1234"))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(response.headers[HttpHeaders.WWWAuthenticate]).isEqualTo("Bearer")
    }
}
