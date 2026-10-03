package com.anksoft.myapplication.core.network

import com.anksoft.myapplication.core.config.AppConfig
import com.anksoft.myapplication.core.network.mock.MockAuthServer
import com.anksoft.myapplication.core.network.mock.createMockEngine
import com.anksoft.myapplication.core.storage.SessionManager
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import org.koin.dsl.module

/**
 * Default HTTP logger. Ktor's Logger.DEFAULT / Logger.SIMPLE are not declared in
 * commonMain, so this tiny implementation serves all platforms.
 */
internal object PrintlnLogger : Logger {
    override fun log(message: String) {
        println(message)
    }
}

/** Auth request timeout per the performance NFR. */
private const val REQUEST_TIMEOUT_MILLIS = 15_000L

val networkModule = module {
    single { MockAuthServer() }
    single {
        val config = get<AppConfig>()
        createHttpClient(
            engine = if (config.useMockBackend) createMockEngine(get()) else null,
            config = config,
            sessionManager = get()
        )
    }
}

/**
 * Builds the app's HttpClient. A null [engine] uses the platform default
 * (OkHttp / Darwin / Js); demo mode and tests pass a MockEngine instead.
 */
fun createHttpClient(
    engine: HttpClientEngine?,
    config: AppConfig,
    sessionManager: SessionManager,
    logger: Logger = PrintlnLogger
): HttpClient {
    val block: HttpClientConfig<*>.() -> Unit = {
        install(ContentNegotiation) {
            json(appJson)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
        }
        install(Auth) {
            bearer {
                // Attaches the stored access token to outgoing requests.
                loadTokens {
                    sessionManager.getToken()?.let { accessToken ->
                        BearerTokens(
                            accessToken = accessToken,
                            refreshToken = sessionManager.getRefreshToken()
                        )
                    }
                }
                // Silent refresh (AC-5.2) needs the backend refresh contract,
                // which does not exist yet -- deliberately left to P1 rather
                // than guessed at. Until then a 401 surfaces to the caller.
            }
        }
        install(Logging) {
            // LogLevel.ALL would print request bodies -- i.e. plaintext
            // passwords -- to logcat. HEADERS only, per AC-1.8.
            this.logger = logger
            level = LogLevel.HEADERS
            // Never print the bearer token.
            sanitizeHeader { header -> header == HttpHeaders.Authorization }
        }
        defaultRequest {
            url(config.baseUrl)
            contentType(ContentType.Application.Json)
        }
    }
    return if (engine == null) HttpClient(block) else HttpClient(engine, block)
}
