package com.anksoft.myapplication.core.network

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.anksoft.myapplication.core.config.TestAppConfigs
import com.anksoft.myapplication.core.logging.NoOpLogger
import com.anksoft.myapplication.core.network.mock.MockAuthServer
import com.anksoft.myapplication.core.storage.SessionManager
import com.anksoft.myapplication.features.auth.data.datasource.AuthRemoteDataSource
import com.anksoft.myapplication.features.auth.data.repository.AuthRepositoryImpl
import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class AuthTokenCacheTest {

    private val server = MockAuthServer()
    private val sentAuthorization = mutableListOf<String?>()
    private val sessionManager = SessionManager(MapSettings())

    /** Auth routes go to the mock server; any other path just records the Authorization header. */
    private val engine = MockEngine { request ->
        if (request.url.encodedPath.contains("auth/me")) {
            sentAuthorization += request.headers[HttpHeaders.Authorization]
            respondOk()
        } else {
            server.handle(this, request)
        }
    }
    private val client: HttpClient = createHttpClient(
        engine = engine,
        config = TestAppConfigs.demo(),
        sessionManager = sessionManager,
        logger = NoOpLogger
    )
    private val repository = AuthRepositoryImpl(
        AuthRemoteDataSource(client, NoOpLogger),
        sessionManager,
        KtorAuthTokenCache(client),
        emptyList(),
        NoOpLogger
    )

    // AC-18
    @Test
    fun requestAfterAccountSwitchCarriesTheNewAccountsToken() = runTest {
        val demo = MockAuthServer.DEMO_USER
        repository.login(demo.email, demo.password)
        val tokenOfA = sessionManager.getToken()
        client.get("auth/me")
        repository.logout()

        repository.register("account-b@example.com", "Password1")
        val tokenOfB = sessionManager.getToken()
        client.get("auth/me")

        assertThat(sentAuthorization).isEqualTo(listOf("Bearer $tokenOfA", "Bearer $tokenOfB"))
    }

    // AC-18: pins Ktor's caching, which is why clear() exists at all.
    @Test
    fun withoutClearingTheClientKeepsSendingThePreviousToken() = runTest {
        sessionManager.saveToken("access-a")
        client.get("auth/me")

        sessionManager.saveToken("access-b")
        client.get("auth/me")

        assertThat(sentAuthorization).isEqualTo(listOf("Bearer access-a", "Bearer access-a"))
    }

    // AC-18
    @Test
    fun clearingTheCacheMakesTheClientReloadTheStoredToken() = runTest {
        sessionManager.saveToken("access-a")
        client.get("auth/me")

        sessionManager.saveToken("access-b")
        KtorAuthTokenCache(client).clear()
        client.get("auth/me")

        assertThat(sentAuthorization).isEqualTo(listOf("Bearer access-a", "Bearer access-b"))
    }
}
