package com.anksoft.myapplication.core.network

import com.anksoft.myapplication.core.config.AppConfig
import com.anksoft.myapplication.core.config.AppEnvironment
import com.anksoft.myapplication.core.logging.AppLogger
import com.anksoft.myapplication.core.logging.LogTags
import com.anksoft.myapplication.core.logging.debug
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
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import org.koin.dsl.module
import io.ktor.client.plugins.logging.Logger as KtorLogger

/** Auth request timeout per the performance NFR. */
private const val REQUEST_TIMEOUT_MILLIS = 15_000L

val networkModule = module {
    single { MockAuthServer() }
    single {
        val config = get<AppConfig>()
        createHttpClient(
            engine = if (config.useMockBackend) createMockEngine(get()) else null,
            config = config,
            sessionManager = get(),
            logger = get()
        )
    }
    single<AuthTokenCache> { KtorAuthTokenCache(get()) }
}

/**
 * Builds the app's HttpClient. A null [engine] uses the platform default
 * (OkHttp / Darwin / Js); demo mode and tests pass a MockEngine instead.
 * HTTP logging goes through [logger] and its level depends on the environment.
 */
fun createHttpClient(
    engine: HttpClientEngine?,
    config: AppConfig,
    sessionManager: SessionManager,
    logger: AppLogger
): HttpClient {
    val httpLogger = KtorLoggerBridge(logger)
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
            // BODY/ALL would log request bodies -- i.e. plaintext passwords -- so they are never
            // used. Headers are logged in DEV only; STAGE and PROD log no HTTP traffic.
            this.logger = httpLogger
            level = config.environment.httpLogLevel()
            // Never log credentials or cookies.
            // Header names are matched case-insensitively: HTTP/2 servers send them lower-cased.
            sanitizeHeader { header -> SENSITIVE_HEADERS.any { it.equals(header, ignoreCase = true) } }
        }
        defaultRequest {
            url(config.baseUrl)
            contentType(ContentType.Application.Json)
        }
    }
    return if (engine == null) HttpClient(block) else HttpClient(engine, block)
}

/** Headers whose values must never reach a log line. */
private val SENSITIVE_HEADERS = listOf(HttpHeaders.Authorization, HttpHeaders.SetCookie, HttpHeaders.Cookie)

/** Forwards Ktor's HTTP log lines to the app logger. */
internal class KtorLoggerBridge(private val logger: AppLogger) : KtorLogger {
    override fun log(message: String) {
        logger.debug(LogTags.HTTP) { message }
    }
}

/** HTTP log level per environment: headers in DEV, nothing in STAGE and PROD. */
internal fun AppEnvironment.httpLogLevel(): LogLevel = when (this) {
    AppEnvironment.DEV -> LogLevel.HEADERS
    AppEnvironment.STAGE, AppEnvironment.PROD -> LogLevel.NONE
}
